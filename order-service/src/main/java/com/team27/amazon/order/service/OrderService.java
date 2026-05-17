package com.team27.amazon.order.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.contracts.dto.OrderDTO;
import com.team27.amazon.contracts.dto.OrderItemDTO;
import com.team27.amazon.contracts.dto.OrderSummaryDTO;
import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ProductSalesAggregateDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.dto.AddOrderItemRequest;
import com.team27.amazon.order.dto.CoPurchaseRecordResponse;
import com.team27.amazon.order.dto.OrderAnalyticsDTO;
import com.team27.amazon.order.dto.OrderAnalyticsDashboardDTO;
import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
import com.team27.amazon.order.dto.OrderItemDetailsDTO;
import com.team27.amazon.order.messaging.publishers.OrderEventPublisher;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderItemRepository;
import com.team27.amazon.order.repository.OrderRepository;

import feign.FeignException;
import jakarta.annotation.PostConstruct;

@Service
public class OrderService extends AbstractEventSubject {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final double SHIPPING_THRESHOLD = 500.0;
    private static final double SHIPPING_FLAT_RATE = 50.0;

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private ShippingServiceClient shippingServiceClient;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Autowired(required = false)
    private OrderRedisCacheService orderRedisCacheService;

    @Autowired
    @Qualifier("orderEventLogger")
    private MongoEventLogger mongoEventLogger;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    private Order reloadOrderWithSortedItems(Long orderId) {
        Order updatedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (updatedOrder.getOrderItems() != null) {
            updatedOrder.getOrderItems().sort(Comparator.comparing(OrderItem::getItemOrder));
        }

        return updatedOrder;
    }

    // CREATE
    public Order createOrder(Order order) {
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.PENDING);
        }

        Order savedOrder = orderRepository.save(order);

        notifyObservers("ORDER_CREATED", orderEventPayload(savedOrder.getId(), Map.of(
                "userId", savedOrder.getUserId(),
                "status", savedOrder.getStatus() == null ? null : savedOrder.getStatus().name(),
                "totalAmount", savedOrder.getTotalAmount()
        )));

        invalidateOrderFeatureCaches();
        return savedOrder;
    }

    @Transactional
    public Order addItemsToExistingOrder(Long orderId, List<AddOrderItemRequest> requests) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending orders can be updated with new items"
            );
        }

        if (requests == null || requests.isEmpty()) {
            return reloadOrderWithSortedItems(orderId);
        }

        List<OrderItem> currentItems = order.getOrderItems();
        if (currentItems == null) {
            currentItems = new ArrayList<>();
            order.setOrderItems(currentItems);
        }

        List<Long> productIds = requests.stream()
                .filter(Objects::nonNull)
                .map(AddOrderItemRequest::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<ProductDTO> products = fetchProductsBatch(productIds);
        Map<Long, ProductDTO> productMap = new HashMap<>();
        for (ProductDTO product : products) {
            productMap.put(product.id(), product);
        }

        int nextItemOrder = currentItems.stream()
                .map(OrderItem::getItemOrder)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        for (AddOrderItemRequest request : requests) {
            if (request == null || request.getProductId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item must include productId"
                );
            }

            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item must include quantity greater than 0"
                );
            }

            ProductDTO product = productMap.get(request.getProductId());
            if (product == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                );
            }

            Double currentPrice = product.price();
            if (currentPrice == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product price not found"
                );
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(request.getProductId());
            orderItem.setQuantity(request.getQuantity());
            orderItem.setPriceAtPurchase(currentPrice);
            orderItem.setItemOrder(nextItemOrder++);
            orderItem.setMetadata(request.getMetadata());

            currentItems.add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);

        notifyObservers("ITEMS_ADDED", orderEventPayload(savedOrder.getId(), Map.of(
                "details", Map.of("addedItems", requests.size())
        )));

        invalidateOrderCaches(orderId);
        invalidateOrderItemFeatureCaches();
        invalidateProductDashboardCache();

        return reloadOrderWithSortedItems(orderId);
    }

    public OrderEstimateDTO estimateOrderPrice(List<OrderEstimateItemRequestDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Items list must not be empty");
        }

        for (OrderEstimateItemRequestDTO item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item must include productId and quantity >= 1"
                );
            }
        }

        String cacheKey = buildFeatureKey("S3-F3", items);
        Optional<OrderEstimateDTO> cached = cacheGet(cacheKey, OrderEstimateDTO.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Long> productIds = items.stream()
                .map(OrderEstimateItemRequestDTO::getProductId)
                .distinct()
                .toList();

        List<ProductDTO> products = fetchProductsBatch(productIds);
        Map<Long, ProductDTO> productMap = new HashMap<>();
        for (ProductDTO product : products) {
            productMap.put(product.id(), product);
        }

        int itemCount = 0;
        double subtotal = 0.0;

        for (OrderEstimateItemRequestDTO item : items) {
            ProductDTO product = productMap.get(item.getProductId());
            if (product == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found: " + item.getProductId()
                );
            }

            Double currentPrice = product.price();
            if (currentPrice == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product price not found"
                );
            }

            itemCount += item.getQuantity();
            subtotal += currentPrice * item.getQuantity();
        }

        double discountApplied = calculateDiscountPercent(itemCount);
        double shippingCost = subtotal >= SHIPPING_THRESHOLD ? 0.0 : SHIPPING_FLAT_RATE;
        double estimatedTotal = (subtotal * (1 - (discountApplied / 100.0))) + shippingCost;

        OrderEstimateDTO estimate = OrderEstimateDTO.builder()
                .itemCount(itemCount)
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .estimatedTotal(estimatedTotal)
                .discountApplied(discountApplied)
                .build();

        cacheSet(cacheKey, estimate, FIVE_MINUTES);
        return estimate;
    }

    private double calculateDiscountPercent(int itemCount) {
        if (itemCount > 15) {
            return 10.0;
        }
        if (itemCount >= 6) {
            return 5.0;
        }
        return 0.0;
    }

    public OrderDetailsDTO getOrderDetails(Long orderId) {
        String cacheKey = "order-service::S3-F9::" + orderId;
        Optional<OrderDetailsDTO> cached = cacheGet(cacheKey, OrderDetailsDTO.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found with id: " + orderId
                ));

        List<OrderItem> orderItems = order.getOrderItems() == null
                ? new ArrayList<>()
                : new ArrayList<>(order.getOrderItems());

        orderItems.sort(Comparator.comparing(OrderItem::getItemOrder));

        List<OrderItemDetailsDTO> itemDTOs = new ArrayList<>();
        int totalQuantity = 0;

        for (OrderItem item : orderItems) {
            itemDTOs.add(new OrderItemDetailsDTO(
                    item.getId(),
                    item.getItemOrder(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.getPriceAtPurchase(),
                    item.getMetadata()
            ));

            totalQuantity += item.getQuantity();
        }

        OrderDetailsDTO detailsDTO = OrderDetailsDTO.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .shippingAddressId(order.getShippingAddressId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .metadata(order.getMetadata())
                .items(itemDTOs)
                .totalItems(itemDTOs.size())
                .totalQuantity(totalQuantity)
                .build();

        cacheSet(cacheKey, detailsDTO, TEN_MINUTES);
        return detailsDTO;
    }

    // READ - Get all orders
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // READ - Get order by ID
    public Optional<Order> getOrderById(Long id) {
        String cacheKey = orderRedisCacheService == null ? null : orderRedisCacheService.orderKey(id);
        Optional<OrderCacheSnapshot> cached = cacheGet(cacheKey, OrderCacheSnapshot.class);
        if (cached.isPresent()) {
            return Optional.of(cached.get().toOrder());
        }

        Optional<Order> dbOrder = orderRepository.findById(id);
        dbOrder.ifPresent(order -> cacheSet(cacheKey, OrderCacheSnapshot.from(order), FIFTEEN_MINUTES));
        return dbOrder;
    }

    // READ - Get orders by user ID
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public OrderDTO getOrderContractById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toOrderDTO(order);
    }

    public List<OrderItemDTO> getOrderItemsForContract(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        List<OrderItem> items = order.getOrderItems() == null ? List.of() : new ArrayList<>(order.getOrderItems());
        items.sort(Comparator.comparing(OrderItem::getItemOrder));

        return items.stream()
                .map(this::toOrderItemDTO)
                .toList();
    }

    public OrderSummaryDTO getUserOrderSummary(Long userId) {
        long totalOrders = orderRepository.countByUserId(userId);
        long completedOrders = orderRepository.countByUserIdAndStatusIn(userId, completedStatuses());
        long cancelledOrders = orderRepository.countByUserIdAndStatus(userId, OrderStatus.CANCELLED);
        double totalSpent = orderRepository.sumTotalAmountByUserIdAndStatusIn(userId, completedStatuses());
        double averageOrderValue = completedOrders == 0 ? 0.0 : totalSpent / completedOrders;
        return new OrderSummaryDTO(totalOrders, completedOrders, cancelledOrders, totalSpent, averageOrderValue);
    }

    public int getActiveOrderCount(Long userId) {
        return (int) orderRepository.countByUserIdAndStatusIn(userId,
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.PAYMENT_PENDING));
    }

    public long getDeliveredOrderCount(Long userId) {
        return orderRepository.countByUserIdAndStatus(userId, OrderStatus.DELIVERED);
    }

    public ProductSalesAggregateDTO getProductSales(Long productId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(LocalTime.MAX);
        Object[] aggregate = orderItemRepository.productSalesAggregate(productId, completedStatuses(), from, to);
        long totalUnitsSold = numberAt(aggregate, 0).longValue();
        double totalRevenue = numberAt(aggregate, 1).doubleValue();
        double averageSellingPrice = totalUnitsSold == 0 ? 0.0 : totalRevenue / totalUnitsSold;
        return new ProductSalesAggregateDTO(totalUnitsSold, totalRevenue, averageSellingPrice);
    }

    public int getPendingOrderCountForProduct(Long productId) {
        return (int) orderItemRepository.countByProductIdAndOrderStatus(productId, OrderStatus.PENDING);
    }

    public long getUnitsSold(Long productId) {
        return orderItemRepository.sumQuantityByProductIdAndOrderStatuses(productId, completedStatuses());
    }

    public int getRecentSalesCount(Long productId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return (int) orderItemRepository.sumQuantityByProductIdAndOrderStatusesSince(productId, completedStatuses(), since);
    }

    public boolean hasUserPurchasedProduct(Long userId, Long productId) {
        return orderItemRepository.existsPurchasedProduct(userId, productId, completedStatuses());
    }

    // READ - Get orders by status
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    // READ - Get orders by user ID and status
    public List<Order> getOrdersByUserIdAndStatus(Long userId, OrderStatus status) {
        return orderRepository.findByUserIdAndStatus(userId, status);
    }

    // READ - Get orders by date range
    public List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByOrderedAtBetween(startDate, endDate).stream()
                .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                .toList();
    }

    public List<Order> searchOrders(OrderStatus status, LocalDate startDate, LocalDate endDate) {
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(LocalTime.MAX);

        String cacheKey = buildFeatureKey("S3-F1", cacheParams(
                "status", status == null ? null : status.name(),
                "startDate", startDate,
                "endDate", endDate
        ));

        Optional<List<Order>> cached = cacheGet(cacheKey, new TypeReference<List<Order>>() {});
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Order> result = orderRepository.findByOrderedAtBetween(rangeStart, rangeEnd).stream()
                .filter(order -> status == null || order.getStatus() == status)
                .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                .toList();

        cacheSet(cacheKey, result, FIVE_MINUTES);
        return result;
    }

    // UPDATE
    public Optional<Order> updateOrder(Long id, Order orderDetails) {
        return orderRepository.findById(id).map(order -> {
            if (orderDetails.getUserId() != null) {
                order.setUserId(orderDetails.getUserId());
            }
            if (orderDetails.getStatus() != null) {
                order.setStatus(orderDetails.getStatus());
            }
            if (orderDetails.getTotalAmount() != null) {
                order.setTotalAmount(orderDetails.getTotalAmount());
            }
            if (orderDetails.getShippingAddressId() != null) {
                order.setShippingAddressId(orderDetails.getShippingAddressId());
            }
            if (orderDetails.getMetadata() != null) {
                order.setMetadata(orderDetails.getMetadata());
            }
            if (orderDetails.getDeliveredAt() != null) {
                order.setDeliveredAt(orderDetails.getDeliveredAt());
            }

            Order savedOrder = orderRepository.save(order);

            notifyObservers("ORDER_UPDATED", orderEventPayload(savedOrder.getId(), Map.of(
                    "status", savedOrder.getStatus() == null ? null : savedOrder.getStatus().name(),
                    "totalAmount", savedOrder.getTotalAmount(),
                    "userId", savedOrder.getUserId()
            )));

            invalidateOrderCaches(savedOrder.getId());
            invalidateProductDashboardCache();

            return savedOrder;
        });
    }

    // DELETE
    public boolean deleteOrder(Long id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            notifyObservers("ORDER_DELETED", orderEventPayload(id, Map.of()));
            invalidateOrderCaches(id);
            invalidateProductDashboardCache();
            return true;
        }

        return false;
    }

    // UTILITY - Calculate total amount from order items
    public Double calculateTotalAmount(Long orderId) {
        return orderRepository.findById(orderId).map(order -> {
            if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                return order.getOrderItems().stream()
                        .mapToDouble(item -> item.getPriceAtPurchase() * item.getQuantity())
                        .sum();
            }
            return 0.0;
        }).orElse(0.0);
    }

    @Transactional
    public Order deliverOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only shipped orders can be marked as delivered"
            );
        }

        if (order.getTotalAmount() == null) {
            List<OrderItem> items = order.getOrderItems() == null ? List.of() : order.getOrderItems();
            double calculated = items.stream()
                    .mapToDouble(item -> (item.getPriceAtPurchase() != null ? item.getPriceAtPurchase() : 0.0)
                            * item.getQuantity())
                    .sum();
            order.setTotalAmount(calculated);
        }

        if (!isActiveUser(order.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is no longer active"
            );
        }

        for (OrderItem orderItem : order.getOrderItems() == null ? List.<OrderItem>of() : order.getOrderItems()) {
            if (!productExists(orderItem.getProductId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product no longer exists in catalog"
                );
            }
        }

        if (!hasActiveShipment(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No active shipment to deliver"
            );
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        orderEventPublisher.publishOrderCompleted(savedOrder);
        log.info("Delivered order {}: published order.completed event", orderId);

        notifyObservers("ORDER_DELIVERED", orderEventPayload(savedOrder.getId(), Map.of(
                "status", savedOrder.getStatus().name(),
                "userId", savedOrder.getUserId(),
                "totalAmount", savedOrder.getTotalAmount()
        )));

        invalidateOrderCaches(savedOrder.getId());
        invalidateProductDashboardCache();

        return savedOrder;
    }

    public List<Order> searchOrdersByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Key and value must not be blank"
            );
        }

        String cacheKey = buildFeatureKey("S3-F5", cacheParams("key", key, "value", value));
        Optional<List<Order>> cached = cacheGet(cacheKey, new TypeReference<List<Order>>() {});
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Order> result = orderRepository.findByMetadataField(key, value);
        cacheSet(cacheKey, result, FIVE_MINUTES);
        return result;
    }

    public OrderAnalyticsDTO getOrderAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range"
            );
        }

        String cacheKey = buildFeatureKey("S3-F6", cacheParams("startDate", startDate, "endDate", endDate));
        Optional<OrderAnalyticsDTO> cached = cacheGet(cacheKey, OrderAnalyticsDTO.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Order> orders = orderRepository.findByOrderedAtBetween(startDate, endDate);

        long totalOrders = orders.size();

        long deliveredOrders = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .count();

        long cancelledOrders = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CANCELLED)
                .count();

        double totalRevenue = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double averageOrderValue = deliveredOrders == 0 ? 0.0 : totalRevenue / deliveredOrders;

        double completionRate = totalOrders == 0 ? 0.0 : (deliveredOrders * 100.0) / totalOrders;

        OrderAnalyticsDTO analyticsDTO = OrderAnalyticsDTO.builder()
                .totalOrders(totalOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .completionRate(completionRate)
                .build();

        cacheSet(cacheKey, analyticsDTO, TEN_MINUTES);
        return analyticsDTO;
    }

    /**
     * S3-F10 Get Order Analytics Dashboard
     */
    public OrderAnalyticsDashboardDTO getOrderAnalyticsDashboard(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(LocalTime.MAX);

        Map<String, Object> details = new HashMap<>();
        details.put("startDate", startDate.toString());
        details.put("endDate", endDate.toString());
        details.put("featureId", "S3-F10");
        details.put("endpoint", "/api/orders/analytics/dashboard");

        notifyObservers("ANALYTICS_VIEWED", orderEventPayload(0L, details));

        String cacheKey = orderRedisCacheService == null
                ? "order-service::S3-F10::" + startDate + "_" + endDate
                : orderRedisCacheService.s3f10DashboardKey(startDate, endDate);

        if (orderRedisCacheService != null) {
            try {
                Optional<OrderAnalyticsDashboardDTO> cached =
                        orderRedisCacheService.get(cacheKey, OrderAnalyticsDashboardDTO.class);
                if (cached.isPresent()) {
                    return cached.get();
                }
            } catch (RuntimeException ex) {
                log.warn("Redis read failed for key {}", cacheKey, ex);
            }
        }

        List<Order> orders = orderRepository.findByOrderedAtBetween(rangeStart, rangeEnd);

        long totalOrders = orders.size();

        double totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double averageOrderValue = totalOrders == 0 ? 0.0 : totalRevenue / (double) totalOrders;

        long deliveredOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .count();

        double completionRate = totalOrders == 0 ? 0.0 : ((double) deliveredOrders) / (double) totalOrders;

        Map<String, Long> ordersByStatus = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status.name(), 0L);
        }

        for (Order order : orders) {
            if (order.getStatus() != null) {
                ordersByStatus.put(
                        order.getStatus().name(),
                        ordersByStatus.getOrDefault(order.getStatus().name(), 0L) + 1
                );
            }
        }

        OrderAnalyticsDashboardDTO dto = OrderAnalyticsDashboardDTO.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .completionRate(completionRate)
                .ordersByStatus(ordersByStatus)
                .build();

        if (orderRedisCacheService != null) {
            try {
                orderRedisCacheService.set(cacheKey, dto, Duration.ofMinutes(10));
            } catch (RuntimeException ex) {
                log.warn("Redis write failed for key {}", cacheKey, ex);
            }
        }

        return dto;
    }

    @Transactional
    public Order confirmOrder(Long orderId, Long shippingAddressId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending orders can be confirmed"
            );
        }

        verifyShippingAddress(order.getUserId(), shippingAddressId);

        List<OrderItem> orderItems = order.getOrderItems() == null ? List.of() : order.getOrderItems();

        if (!orderItems.isEmpty()) {
            List<Long> productIds = orderItems.stream()
                    .map(OrderItem::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<ProductDTO> products = fetchProductsBatch(productIds);
            Map<Long, ProductDTO> productMap = new HashMap<>();
            for (ProductDTO product : products) {
                productMap.put(product.id(), product);
            }

            for (OrderItem orderItem : orderItems) {
                ProductDTO product = productMap.get(orderItem.getProductId());
                if (product == null) {
                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Product not found: " + orderItem.getProductId()
                    );
                }

                if (product.stockQuantity() == null || product.stockQuantity() < orderItem.getQuantity()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Insufficient stock for product " + orderItem.getProductId()
                    );
                }
            }
        }

        double totalAmount = orderItems.stream()
                .mapToDouble(item -> item.getQuantity() * item.getPriceAtPurchase())
                .sum();

        order.setShippingAddressId(shippingAddressId);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        orderEventPublisher.publishOrderPlaced(savedOrder);
        log.info("Confirmed order {}: published order.placed event", orderId);

        notifyObservers("ORDER_CONFIRMED", orderEventPayload(savedOrder.getId(), Map.of(
                "status", savedOrder.getStatus().name(),
                "userId", savedOrder.getUserId(),
                "totalAmount", savedOrder.getTotalAmount(),
                "shippingAddressId", savedOrder.getShippingAddressId()
        )));

        invalidateOrderCaches(savedOrder.getId());
        invalidateProductDashboardCache();

        return savedOrder;
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending or confirmed orders can be cancelled"
            );
        }

        List<OrderItem> restoredItems = new ArrayList<>();
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            restoredItems = order.getOrderItems() == null ? List.of() : new ArrayList<>(order.getOrderItems());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        orderEventPublisher.publishOrderCancelled(savedOrder, restoredItems, "user_requested");
        log.info("Cancelled order {}: published order.cancelled event", orderId);

        Map<String, Object> cancelDetails = new HashMap<>();
        cancelDetails.put("status", savedOrder.getStatus().name());
        if (savedOrder.getUserId() != null) {
            cancelDetails.put("userId", savedOrder.getUserId());
        }
        if (savedOrder.getTotalAmount() != null) {
            cancelDetails.put("totalAmount", savedOrder.getTotalAmount());
        }

        notifyObservers("ORDER_CANCELLED", orderEventPayload(savedOrder.getId(), cancelDetails));

        invalidateOrderCaches(savedOrder.getId());
        invalidateProductDashboardCache();

        return savedOrder;
    }

    @Transactional
    public CoPurchaseRecordResponse recordProductCoPurchase(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only DELIVERED orders can be recorded for co-purchase"
            );
        }

        List<Long> productIds = order.getOrderItems().stream()
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (productIds.size() < 2) {
            return new CoPurchaseRecordResponse(
                    orderId,
                    productIds,
                    0,
                    false,
                    "Order has fewer than two distinct products; no co-purchase pairs recorded"
            );
        }

        boolean alreadyRecorded = neo4jClient.query("""
                    MATCH (ro:RecordedOrder {orderId: $orderId})
                    RETURN count(ro) > 0 AS recorded
                    """)
                .bind(orderId).to("orderId")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);

        if (alreadyRecorded) {
            return new CoPurchaseRecordResponse(
                    orderId,
                    productIds,
                    0,
                    true,
                    "Order was already recorded; no changes made"
            );
        }

        Map<Long, ProductSnapshot> snapshots = loadProductSnapshots(productIds);

        for (Long productId : productIds) {
            ProductSnapshot snapshot = snapshots.get(productId);

            neo4jClient.query("""
                        MERGE (p:ProductNode {productId: $productId})
                        SET p.name = $name,
                            p.category = $category
                        """)
                    .bind(productId).to("productId")
                    .bind(snapshot == null ? "" : snapshot.name).to("name")
                    .bind(snapshot == null ? "" : snapshot.category).to("category")
                    .run();
        }

        int pairsRecorded = 0;

        for (int i = 0; i < productIds.size(); i++) {
            for (int j = i + 1; j < productIds.size(); j++) {
                Long firstId = productIds.get(i);
                Long secondId = productIds.get(j);

                neo4jClient.query("""
                            MATCH (a:ProductNode {productId: $firstId})
                            MATCH (b:ProductNode {productId: $secondId})
                            MERGE (a)-[r:BOUGHT_TOGETHER]->(b)
                            ON CREATE SET
                                r.coPurchaseCount = 1,
                                r.lastCoPurchaseDate = datetime()
                            ON MATCH SET
                                r.coPurchaseCount = coalesce(r.coPurchaseCount, 0) + 1,
                                r.lastCoPurchaseDate = datetime()
                            """)
                        .bind(firstId).to("firstId")
                        .bind(secondId).to("secondId")
                        .run();

                pairsRecorded++;
            }
        }

        neo4jClient.query("""
                    MERGE (:RecordedOrder {orderId: $orderId})
                    """)
                .bind(orderId).to("orderId")
                .run();

        var cache = cacheManager.getCache("order:recommendations");
        if (cache != null) {
            cache.clear();
        }

        notifyObservers("INTERACTION_RECORDED", Map.of(
                "orderId", orderId,
                "details", Map.of(
                        "orderId", orderId,
                        "productIds", productIds,
                        "pairsRecorded", pairsRecorded
                )
        ));

        return new CoPurchaseRecordResponse(
                orderId,
                productIds,
                pairsRecorded,
                false,
                "Co-purchase relationships recorded successfully"
        );
    }

    private Map<Long, ProductSnapshot> loadProductSnapshots(List<Long> productIds) {
        Map<Long, ProductSnapshot> snapshots = new HashMap<>();

        for (ProductDTO product : fetchProductsBatch(productIds)) {
            snapshots.put(product.id(), new ProductSnapshot(
                    product.id(),
                    product.name() == null ? "" : product.name(),
                    product.category() == null ? "" : product.category()
            ));
        }

        return snapshots;
    }

    private List<OrderStatus> completedStatuses() {
        return List.of(OrderStatus.DELIVERED, OrderStatus.PAID);
    }

    private Number numberAt(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0;
        }
        return (Number) row[index];
    }

    private OrderDTO toOrderDTO(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getUserId(),
                order.getShippingAddressId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                order.getDeliveredAt(),
                order.getMetadata()
        );
    }

    private OrderItemDTO toOrderItemDTO(OrderItem item) {
        return new OrderItemDTO(
                item.getId(),
                item.getOrder() == null ? null : item.getOrder().getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getPriceAtPurchase(),
                item.getItemOrder(),
                item.getMetadata()
        );
    }

    private ProductDTO fetchProduct(Long productId) {
        try {
            return productServiceClient.getProduct(productId);
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found", ex);
        } catch (FeignException ex) {
            log.warn("product-service unavailable for product {}: {}", productId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service temporarily unavailable", ex);
        }
    }

    private List<ProductDTO> fetchProductsBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        try {
            return productServiceClient.getProductsBatch(productIds);
        } catch (FeignException ex) {
            log.warn("product-service batch lookup failed for products {}: {}", productIds, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service temporarily unavailable", ex);
        }
    }

    private void verifyShippingAddress(Long userId, Long shippingAddressId) {
        try {
            userServiceClient.getShippingAddress(userId, shippingAddressId);
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipping address not found", ex);
        } catch (FeignException ex) {
            log.warn("user-service unavailable for address {} of user {}: {}", shippingAddressId, userId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable", ex);
        }
    }

    private boolean isActiveUser(Long userId) {
        try {
            return "ACTIVE".equalsIgnoreCase(userServiceClient.getUser(userId).status());
        } catch (FeignException.NotFound ex) {
            return false;
        } catch (FeignException ex) {
            log.warn("user-service unavailable for user {}: {}", userId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable", ex);
        }
    }

    private boolean productExists(Long productId) {
        try {
            return productServiceClient.productExists(productId).exists();
        } catch (FeignException.NotFound ex) {
            return false;
        } catch (FeignException ex) {
            log.warn("product-service existence check failed for product {}: {}", productId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service temporarily unavailable", ex);
        }
    }

    private boolean hasActiveShipment(Long orderId) {
        try {
            shippingServiceClient.getActiveShipmentForOrder(orderId);
            return true;
        } catch (FeignException.NotFound ex) {
            return false;
        } catch (FeignException ex) {
            log.warn("shipping-service active shipment check failed for order {}: {}", orderId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shipping service temporarily unavailable", ex);
        }
    }

    private Map<String, Object> cacheParams(Object... keyValues) {
        if (orderRedisCacheService == null) {
            return new HashMap<>();
        }
        return orderRedisCacheService.orderedParams(keyValues);
    }

    private String buildFeatureKey(String featureId, Object params) {
        if (orderRedisCacheService == null) {
            return "";
        }
        return orderRedisCacheService.featureKey(featureId, params);
    }

    private <T> Optional<T> cacheGet(String key, Class<T> type) {
        if (orderRedisCacheService == null || key == null || key.isBlank()) {
            return Optional.empty();
        }

        try {
            return orderRedisCacheService.get(key, type);
        } catch (RuntimeException ex) {
            log.warn("Redis get failed for key {}", key, ex);
            return Optional.empty();
        }
    }

    private <T> Optional<T> cacheGet(String key, TypeReference<T> typeReference) {
        if (orderRedisCacheService == null || key == null || key.isBlank()) {
            return Optional.empty();
        }

        try {
            return orderRedisCacheService.get(key, typeReference);
        } catch (RuntimeException ex) {
            log.warn("Redis get failed for key {}", key, ex);
            return Optional.empty();
        }
    }

    private void cacheSet(String key, Object value, Duration ttl) {
        if (orderRedisCacheService == null || key == null || key.isBlank()) {
            return;
        }

        try {
            orderRedisCacheService.set(key, value, ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis set failed for key {}", key, ex);
        }
    }

    private void invalidateOrderCaches(Long orderId) {
        if (orderRedisCacheService == null) {
            return;
        }

        if (orderId != null) {
            try {
                orderRedisCacheService.delete(orderRedisCacheService.orderKey(orderId));
            } catch (RuntimeException ex) {
                log.warn("Redis order cache delete failed for order {}", orderId, ex);
            }
        }

        invalidateOrderFeatureCaches();
    }

    private void invalidateOrderFeatureCaches() {
        if (orderRedisCacheService == null) {
            return;
        }

        for (String featureId : new LinkedHashSet<>(List.of("S3-F1", "S3-F3", "S3-F5", "S3-F6", "S3-F9", "S3-F10"))) {
            try {
                orderRedisCacheService.deleteByPattern("order-service::" + featureId + "::*");
            } catch (RuntimeException ex) {
                log.warn("Redis wildcard delete failed for feature {}", featureId, ex);
            }
        }
    }

    private void invalidateOrderItemFeatureCaches() {
        if (orderRedisCacheService == null) {
            return;
        }

        for (String featureId : List.of("S3-F3", "S3-F6", "S3-F9", "S3-F10")) {
            try {
                orderRedisCacheService.deleteByPattern("order-service::" + featureId + "::*");
            } catch (RuntimeException ex) {
                log.warn("Redis wildcard delete failed for feature {}", featureId, ex);
            }
        }
    }

    private void invalidateProductDashboardCache() {
        if (orderRedisCacheService == null) {
            return;
        }

        try {
            orderRedisCacheService.deleteByPattern("product-service::S2-F12::*");
        } catch (RuntimeException ex) {
            log.warn("Redis product dashboard cache invalidation failed", ex);
        }
    }

    private Map<String, Object> orderEventPayload(Long orderId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private static class ProductSnapshot {

        private final Long productId;
        private final String name;
        private final String category;

        private ProductSnapshot(Long productId, String name, String category) {
            this.productId = productId;
            this.name = name;
            this.category = category;
        }
    }

    static final class OrderCacheSnapshot {

        private Long id;
        private Long userId;
        private Long shippingAddressId;
        private String status;
        private Double totalAmount;
        private Map<String, Object> metadata;
        private String orderedAt;
        private String deliveredAt;

        public OrderCacheSnapshot() {
        }

        static OrderCacheSnapshot from(Order order) {
            OrderCacheSnapshot snapshot = new OrderCacheSnapshot();
            snapshot.setId(order.getId());
            snapshot.setUserId(order.getUserId());
            snapshot.setShippingAddressId(order.getShippingAddressId());
            snapshot.setStatus(order.getStatus() == null ? null : order.getStatus().name());
            snapshot.setTotalAmount(order.getTotalAmount());
            snapshot.setMetadata(order.getMetadata() == null ? new HashMap<>() : new HashMap<>(order.getMetadata()));
            snapshot.setOrderedAt(order.getOrderedAt() == null ? null : order.getOrderedAt().toString());
            snapshot.setDeliveredAt(order.getDeliveredAt() == null ? null : order.getDeliveredAt().toString());
            return snapshot;
        }

        Order toOrder() {
            Order order = new Order();
            order.setId(id);
            order.setUserId(userId);
            order.setShippingAddressId(shippingAddressId);

            if (status != null) {
                order.setStatus(OrderStatus.valueOf(status));
            }

            order.setTotalAmount(totalAmount);
            order.setMetadata(metadata == null ? new HashMap<>() : new HashMap<>(metadata));

            if (orderedAt != null) {
                order.setOrderedAt(LocalDateTime.parse(orderedAt));
            }

            if (deliveredAt != null) {
                order.setDeliveredAt(LocalDateTime.parse(deliveredAt));
            }

            return order;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getShippingAddressId() {
            return shippingAddressId;
        }

        public void setShippingAddressId(Long shippingAddressId) {
            this.shippingAddressId = shippingAddressId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Double getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public String getOrderedAt() {
            return orderedAt;
        }

        public void setOrderedAt(String orderedAt) {
            this.orderedAt = orderedAt;
        }

        public String getDeliveredAt() {
            return deliveredAt;
        }

        public void setDeliveredAt(String deliveredAt) {
            this.deliveredAt = deliveredAt;
        }
    }
}