package com.team27.amazon.order.service;

import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@Service
public class OrderItemService {

    private static final Logger log = LoggerFactory.getLogger(OrderItemService.class);

    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired(required = false)
    private OrderRedisCacheService orderRedisCacheService;

    // CREATE
    public OrderItem createOrderItem(OrderItem orderItem) {
        OrderItem saved = orderItemRepository.save(orderItem);
        invalidateOrderItemCaches(saved.getId(), saved.getOrder() == null ? null : saved.getOrder().getId());
        invalidateProductDashboardCacheIfProductAffected(saved.getProductId());
        return saved;
    }

    // READ - Get all order items
    public List<OrderItem> getAllOrderItems() {
        return orderItemRepository.findAll();
    }

    // READ - Get order item by ID
    public Optional<OrderItem> getOrderItemById(Long id) {
        String cacheKey = orderRedisCacheService == null ? null : orderRedisCacheService.orderItemKey(id);
        Optional<OrderItemCacheSnapshot> cached = cacheGet(cacheKey, OrderItemCacheSnapshot.class);
        if (cached.isPresent()) {
            return Optional.of(cached.get().toOrderItem());
        }

        Optional<OrderItem> dbItem = orderItemRepository.findById(id);
        dbItem.ifPresent(item -> cacheSet(cacheKey, OrderItemCacheSnapshot.from(item), FIFTEEN_MINUTES));
        return dbItem;
    }

    // READ - Get order items by order ID
    public List<OrderItem> getOrderItemsByOrderId(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    // READ - Get order items by product ID
    public List<OrderItem> getOrderItemsByProductId(Long productId) {
        return orderItemRepository.findByProductId(productId);
    }

    // UPDATE
    public Optional<OrderItem> updateOrderItem(Long id, OrderItem itemDetails) {
        return orderItemRepository.findById(id).map(item -> {
            if (itemDetails.getProductId() != null) {
                item.setProductId(itemDetails.getProductId());
            }
            if (itemDetails.getQuantity() != null) {
                item.setQuantity(itemDetails.getQuantity());
            }
            if (itemDetails.getPriceAtPurchase() != null) {
                item.setPriceAtPurchase(itemDetails.getPriceAtPurchase());
            }
            if (itemDetails.getItemOrder() != null) {
                item.setItemOrder(itemDetails.getItemOrder());
            }
            if (itemDetails.getMetadata() != null) {
                item.setMetadata(itemDetails.getMetadata());
            }
            OrderItem saved = orderItemRepository.save(item);
            invalidateOrderItemCaches(saved.getId(), saved.getOrder() == null ? null : saved.getOrder().getId());
            invalidateProductDashboardCacheIfProductAffected(saved.getProductId());
            return saved;
        });
    }

    // DELETE
    public boolean deleteOrderItem(Long id) {
        Optional<OrderItem> existing = orderItemRepository.findById(id);
        if (existing.isPresent()) {
            orderItemRepository.deleteById(id);
            Long orderId = existing.get().getOrder() == null ? null : existing.get().getOrder().getId();
            invalidateOrderItemCaches(id, orderId);
            invalidateProductDashboardCacheIfProductAffected(existing.get().getProductId());
            return true;
        }
        return false;
    }

    private void invalidateOrderItemCaches(Long orderItemId, Long orderId) {
        if (orderRedisCacheService == null) {
            return;
        }

        if (orderItemId != null) {
            try {
                orderRedisCacheService.delete(orderRedisCacheService.orderItemKey(orderItemId));
            } catch (RuntimeException ex) {
                // Redis is a soft dependency; ignore cache delete failures.
            }
        }
        if (orderId != null) {
            try {
                orderRedisCacheService.delete(orderRedisCacheService.orderKey(orderId));
            } catch (RuntimeException ex) {
                // Redis is a soft dependency; ignore cache delete failures.
            }
        }

        for (String pattern : List.of(
                "order-service::S3-F3::*",
                "order-service::S3-F6::*",
                "order-service::S3-F9::*",
                "order-service::S3-F10::*"
        )) {
            try {
                orderRedisCacheService.deleteByPattern(pattern);
            } catch (RuntimeException ex) {
                // Redis is a soft dependency; ignore wildcard delete failures.
            }
        }
    }

    private void invalidateProductDashboardCacheIfProductAffected(Long productId) {
        if (orderRedisCacheService == null || productId == null) {
            return;
        }
        try {
            orderRedisCacheService.deleteByPattern("product-service::S2-F12::*");
        } catch (RuntimeException ex) {
            // Redis is a soft dependency; ignore wildcard delete failures.
        }
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

    static final class OrderItemCacheSnapshot {
        private Long id;
        private Long productId;
        private Integer quantity;
        private Double priceAtPurchase;
        private Integer itemOrder;
        private Map<String, Object> metadata;
        private Long orderId;

        public OrderItemCacheSnapshot() {
        }

        static OrderItemCacheSnapshot from(OrderItem item) {
            OrderItemCacheSnapshot snapshot = new OrderItemCacheSnapshot();
            snapshot.setId(item.getId());
            snapshot.setProductId(item.getProductId());
            snapshot.setQuantity(item.getQuantity());
            snapshot.setPriceAtPurchase(item.getPriceAtPurchase());
            snapshot.setItemOrder(item.getItemOrder());
            snapshot.setMetadata(item.getMetadata() == null ? new HashMap<>() : new HashMap<>(item.getMetadata()));
            snapshot.setOrderId(item.getOrder() == null ? null : item.getOrder().getId());
            return snapshot;
        }

        OrderItem toOrderItem() {
            OrderItem item = new OrderItem();
            item.setId(id);
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setPriceAtPurchase(priceAtPurchase);
            item.setItemOrder(itemOrder);
            item.setMetadata(metadata == null ? new HashMap<>() : new HashMap<>(metadata));
            if (orderId != null) {
                com.team27.amazon.order.model.Order order = new com.team27.amazon.order.model.Order();
                order.setId(orderId);
                item.setOrder(order);
            }
            return item;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Double getPriceAtPurchase() {
            return priceAtPurchase;
        }

        public void setPriceAtPurchase(Double priceAtPurchase) {
            this.priceAtPurchase = priceAtPurchase;
        }

        public Integer getItemOrder() {
            return itemOrder;
        }

        public void setItemOrder(Integer itemOrder) {
            this.itemOrder = itemOrder;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
    }
}

