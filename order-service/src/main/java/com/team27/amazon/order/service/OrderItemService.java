package com.team27.amazon.order.service;

import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class OrderItemService {

    @Autowired
    private OrderItemRepository orderItemRepository;

    // CREATE
    public OrderItem createOrderItem(OrderItem orderItem) {
        return orderItemRepository.save(orderItem);
    }

    // READ - Get all order items
    public List<OrderItem> getAllOrderItems() {
        return orderItemRepository.findAll();
    }

    // READ - Get order item by ID
    public Optional<OrderItem> getOrderItemById(Long id) {
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
            return orderItemRepository.save(item);
        });
    }

    // DELETE
    public boolean deleteOrderItem(Long id) {
        if (orderItemRepository.existsById(id)) {
            orderItemRepository.deleteById(id);
            return true;
        }
        return false;
    }
}

