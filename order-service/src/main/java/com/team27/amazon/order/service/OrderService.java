package com.team27.amazon.order.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.AddOrderItemRequest;
import com.team27.amazon.order.dto.OrderAnalyticsDTO;
import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
import com.team27.amazon.order.dto.OrderItemDetailsDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderItemRepository;
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
    private OrderItemRepository orderItemRepository;


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
        return reloadOrderWithSortedItems(orderId);
    }

    public OrderEstimateDTO estimateOrderPrice(List<OrderEstimateItemRequestDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Items list must not be empty");
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

        return new OrderEstimateDTO(itemCount, subtotal, shippingCost, estimatedTotal, discountApplied);
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

        return new OrderDetailsDTO(
                order.getId(),
                order.getUserId(),
                order.getShippingAddressId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getMetadata(),
                itemDTOs,
                itemDTOs.size(),
                totalQuantity
        );
    }
    // READ - Get all orders
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // READ - Get order by ID
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
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

        return orderRepository.findByOrderedAtBetween(rangeStart, rangeEnd).stream()
                .filter(order -> status == null || order.getStatus() == status)
                .sorted(Comparator.comparing(Order::getOrderedAt).reversed())
                .toList();
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
            return savedOrder;
        });
    }

    // DELETE
    public boolean deleteOrder(Long id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            notifyObservers("ORDER_DELETED", orderEventPayload(id, Map.of()));
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

        return savedOrder;
    }
    public List<Order> searchOrdersByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Key and value must not be blank"
            );
        }

        return orderRepository.findByMetadataField(key, value);
    }
    public OrderAnalyticsDTO getOrderAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range"
            );
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

        return new OrderAnalyticsDTO(
                totalOrders,
                deliveredOrders,
                cancelledOrders,
                totalRevenue,
                averageOrderValue,
                completionRate
        );
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
        notifyObservers("ORDER_CANCELLED", orderEventPayload(savedOrder.getId(), Map.of(
                "status", savedOrder.getStatus().name(),
                "userId", savedOrder.getUserId(),
                "totalAmount", savedOrder.getTotalAmount()
        )));
        return savedOrder;
    }

    private Map<String, Object> orderEventPayload(Long orderId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }
}

