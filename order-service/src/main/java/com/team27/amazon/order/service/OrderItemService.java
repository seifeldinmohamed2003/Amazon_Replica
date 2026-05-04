package com.team27.amazon.order.service;

import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class OrderItemService {

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
        if (orderRedisCacheService != null) {
            String cacheKey = orderRedisCacheService.orderItemKey(id);
            Optional<OrderItem> cached;
            try {
                cached = orderRedisCacheService.get(cacheKey, OrderItem.class);
            } catch (RuntimeException ex) {
                cached = Optional.empty();
            }
            if (cached.isPresent()) {
                return cached;
            }

            Optional<OrderItem> dbItem = orderItemRepository.findById(id);
            dbItem.ifPresent(item -> {
                try {
                    orderRedisCacheService.set(cacheKey, item, FIFTEEN_MINUTES);
                } catch (RuntimeException ex) {
                    // Redis is a soft dependency; ignore cache write failures.
                }
            });
            return dbItem;
        }
        return orderItemRepository.findById(id);
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
}

