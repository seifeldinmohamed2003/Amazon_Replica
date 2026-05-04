package com.team27.amazon.order.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashSet;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.AddOrderItemRequest;
import com.team27.amazon.order.dto.OrderAnalyticsDTO;
import com.team27.amazon.order.dto.OrderAnalyticsDashboardDTO;
import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
import com.team27.amazon.order.dto.OrderItemDetailsDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;
import com.team27.amazon.order.repository.ShipmentJdbcRepository;
import com.team27.amazon.order.repository.ShippingAddressJdbcRepository;
import com.team27.amazon.order.repository.TransactionJdbcRepository;

import jakarta.annotation.PostConstruct;

@Service
public class OrderService extends AbstractEventSubject {

    private static final double SHIPPING_THRESHOLD = 500.0;
    private static final double SHIPPING_FLAT_RATE = 50.0;
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ShipmentJdbcRepository shipmentJdbcRepository;

    @Autowired
    private ShippingAddressJdbcRepository shippingAddressJdbcRepository;

    @Autowired
    private ProductJdbcRepository productJdbcRepository;
    
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
    @Autowired
    private TransactionJdbcRepository transactionJdbcRepository;

    @Autowired(required = false)
    private OrderRedisCacheService orderRedisCacheService;

    @Autowired
    @Qualifier("orderEventLogger")
    private MongoEventLogger mongoEventLogger;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
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

            if (!productJdbcRepository.existsByProductId(request.getProductId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                );
            }

            Double currentPrice = productJdbcRepository.findCurrentPriceByProductId(request.getProductId());
            if (currentPrice == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
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

        String cacheKey = buildFeatureKey("S3-F3", items);
        Optional<OrderEstimateDTO> cached = cacheGet(cacheKey, OrderEstimateDTO.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        int itemCount = 0;
        double subtotal = 0.0;

        for (OrderEstimateItemRequestDTO item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item must include productId and quantity >= 1");
            }

            Double currentPrice = productJdbcRepository.findCurrentPriceByProductId(item.getProductId());
            if (currentPrice == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
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
        String cacheKey = "order-service::order::" + id;
        Optional<Order> cached = cacheGet(cacheKey, Order.class);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Order> dbOrder = orderRepository.findById(id);
        dbOrder.ifPresent(order -> cacheSet(cacheKey, order, FIFTEEN_MINUTES));
        return dbOrder;
    }

    // READ - Get orders by user ID
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
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
                        HttpStatus.NOT_FOUND, "Order not found"
                ));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only shipped orders can be marked as delivered"
            );
        }

        if (!shipmentJdbcRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Shipment not found for this order"
            );
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        shipmentJdbcRepository.markDeliveredByOrderId(
                orderId,
                LocalDate.now(),
                LocalDateTime.now()
        );

        Double totalAmount = savedOrder.getTotalAmount();
        double amount = totalAmount == null ? 0.0 : totalAmount;

        transactionJdbcRepository.insertPendingTransaction(
                savedOrder.getId(),
                savedOrder.getUserId(),
                amount,
                LocalDateTime.now()
        );

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

        // Expand to full-day range
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(java.time.LocalTime.MAX);

        // Prepare analytics viewed event payload and notify observers (orderId=0 for dashboard-level events)
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
                Optional<OrderAnalyticsDashboardDTO> cached = orderRedisCacheService.get(cacheKey, OrderAnalyticsDashboardDTO.class);
                if (cached.isPresent()) {
                    return cached.get();
                }
            } catch (RuntimeException ex) {
                // Soft dependency: log and continue
                System.err.println("Warning: Redis read failed: " + ex.getMessage());
            }
        }

        // Compute from DB
        List<Order> orders = orderRepository.findByOrderedAtBetween(rangeStart, rangeEnd);

        long totalOrders = orders.size();

        double totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double averageOrderValue = totalOrders == 0 ? 0.0 : totalRevenue / (double) totalOrders;

        long deliveredOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();

        double completionRate = totalOrders == 0 ? 0.0 : ((double) deliveredOrders) / (double) totalOrders;

        // Build ordersByStatus with all supported statuses included
        Map<String, Long> ordersByStatus = new HashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            ordersByStatus.put(s.name(), 0L);
        }
        orders.stream().forEach(o -> ordersByStatus.put(o.getStatus().name(), ordersByStatus.getOrDefault(o.getStatus().name(), 0L) + 1));

        OrderAnalyticsDashboardDTO dto = OrderAnalyticsDashboardDTO.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .completionRate(completionRate)
                .ordersByStatus(ordersByStatus)
                .build();

        // Try Redis write
        if (orderRedisCacheService != null) {
            try {
                orderRedisCacheService.set(cacheKey, dto, Duration.ofMinutes(10));
            } catch (RuntimeException ex) {
                System.err.println("Warning: Redis write failed: " + ex.getMessage());
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

        if (!shippingAddressJdbcRepository.existsByShippingAddressId(shippingAddressId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Shipping address not found"
            );
        }

        List<OrderItem> orderItems = order.getOrderItems() == null ? List.of() : order.getOrderItems();
        double totalAmount = 0.0;

        for (OrderItem orderItem : orderItems) {
            if (!productJdbcRepository.existsByProductId(orderItem.getProductId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                );
            }

            Integer stockQuantity = productJdbcRepository.findStockQuantityByProductId(orderItem.getProductId());
            if (stockQuantity == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                );
            }

            if (stockQuantity < orderItem.getQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Insufficient stock for product " + orderItem.getProductId()
                );
            }
        }

        for (OrderItem orderItem : orderItems) {
            int updatedRows = productJdbcRepository.deductStockQuantity(
                    orderItem.getProductId(),
                    orderItem.getQuantity()
            );
            if (updatedRows == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Insufficient stock for product " + orderItem.getProductId()
                );
            }
            totalAmount += orderItem.getQuantity() * orderItem.getPriceAtPurchase();
        }

        order.setShippingAddressId(shippingAddressId);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);
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

        // If order was confirmed, restore stock for each item
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            List<OrderItem> orderItems = order.getOrderItems() == null ? List.of() : order.getOrderItems();
            for (OrderItem orderItem : orderItems) {
                productJdbcRepository.restoreStockQuantity(
                        orderItem.getProductId(),
                        orderItem.getQuantity()
                );
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
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
            // Redis is a soft dependency; ignore cache write failures.
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
                // Redis is a soft dependency; ignore cache delete failures.
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
                // Redis is a soft dependency; ignore wildcard delete failures.
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
                // Redis is a soft dependency; ignore wildcard delete failures.
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
            // Redis is a soft dependency; ignore wildcard delete failures.
        }
    }

    private Map<String, Object> orderEventPayload(Long orderId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

}

