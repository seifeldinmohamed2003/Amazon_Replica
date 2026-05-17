package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ShipmentDTO;
import com.team27.amazon.contracts.dto.UserDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.order.messaging.publishers.OrderEventPublisher;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;
import com.team27.amazon.order.repository.ShipmentJdbcRepository;
import com.team27.amazon.order.repository.ShippingAddressJdbcRepository;
import com.team27.amazon.order.repository.TransactionJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for S3-F4 deliver order with saga pre-checks and order.completed event publishing.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceDeliverTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentJdbcRepository shipmentJdbcRepository;

    @Mock
    private ShippingAddressJdbcRepository shippingAddressJdbcRepository;

    @Mock
    private ProductJdbcRepository productJdbcRepository;

    @Mock
    private TransactionJdbcRepository transactionJdbcRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private ShippingServiceClient shippingServiceClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Order shippedOrder;
    private OrderItem item1;
    private OrderItem item2;

    @BeforeEach
    void setUp() {
        item1 = new OrderItem();
        item1.setId(1L);
        item1.setProductId(101L);
        item1.setQuantity(2);
        item1.setPriceAtPurchase(50.0);

        item2 = new OrderItem();
        item2.setId(2L);
        item2.setProductId(102L);
        item2.setQuantity(1);
        item2.setPriceAtPurchase(75.0);

        shippedOrder = new Order();
        shippedOrder.setId(99L);
        shippedOrder.setUserId(42L);
        shippedOrder.setStatus(OrderStatus.SHIPPED);
        shippedOrder.setTotalAmount(175.0);
        shippedOrder.setShippingAddressId(5L);
        shippedOrder.setMetadata(new HashMap<>());
        shippedOrder.setOrderItems(List.of(item1, item2));
    }

    @Test
    void deliverOrderSucceedsWhenAllPreChecksPass() {
        // Mock order repository
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        // Mock user check
        UserDTO activeUser = new UserDTO(42L, "John Doe", "john@example.com", "555-1234", "CUSTOMER", "ACTIVE", Map.of());
        when(userServiceClient.getUser(42L)).thenReturn(activeUser);

        // Mock product checks
        List<ProductDTO> products = List.of(
                new ProductDTO(101L, "P101", "desc", 50.0, "cat", "brand", 10, "ACTIVE", 4.5, Map.of()),
                new ProductDTO(102L, "P102", "desc", 75.0, "cat", "brand", 5, "ACTIVE", 4.0, Map.of())
        );
        when(productServiceClient.getProductsBatch(anyList())).thenReturn(products);

        // Mock shipment check
        ShipmentDTO shipment = new ShipmentDTO(200L, 99L, "FedEx", "TRACK123", "SHIPPED", null, null, LocalDateTime.now(), null, null, Map.of());
        when(shippingServiceClient.getActiveShipmentForOrder(99L)).thenReturn(shipment);

        // Mock save to return the delivered order
        Order expectedDelivered = new Order();
        expectedDelivered.setId(99L);
        expectedDelivered.setUserId(42L);
        expectedDelivered.setStatus(OrderStatus.DELIVERED);
        expectedDelivered.setTotalAmount(175.0);
        expectedDelivered.setDeliveredAt(LocalDateTime.now());
        expectedDelivered.setOrderItems(shippedOrder.getOrderItems());
        when(orderRepository.save(any(Order.class))).thenReturn(expectedDelivered);

        // Execute
        Order result = orderService.deliverOrder(99L);

        // Verify
        assertEquals(OrderStatus.DELIVERED, result.getStatus());
        assertNotNull(result.getDeliveredAt());
        verify(orderRepository).save(any(Order.class));
        verify(orderEventPublisher).publishOrderCompleted(any(Order.class));
        verify(userServiceClient).getUser(42L);
        verify(productServiceClient).getProductsBatch(List.of(101L, 102L));
        verify(shippingServiceClient).getActiveShipmentForOrder(99L);
        // Verify no direct shipment/transaction updates
        verify(shipmentJdbcRepository, never()).markDeliveredByOrderId(anyLong(), any(), any());
        verify(transactionJdbcRepository, never()).insertPendingTransaction(anyLong(), anyLong(), anyDouble(), any());
    }

    @Test
    void deliverOrderCalculatesTotalAmountFromItemsIfNull() {
        // Setup order without totalAmount
        shippedOrder.setTotalAmount(null);
        
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        UserDTO activeUser = new UserDTO(42L, "John Doe", "john@example.com", "555-1234", "CUSTOMER", "ACTIVE", Map.of());
        when(userServiceClient.getUser(42L)).thenReturn(activeUser);

        List<ProductDTO> products = List.of(
                new ProductDTO(101L, "P101", "desc", 50.0, "cat", "brand", 10, "ACTIVE", 4.5, Map.of()),
                new ProductDTO(102L, "P102", "desc", 75.0, "cat", "brand", 5, "ACTIVE", 4.0, Map.of())
        );
        when(productServiceClient.getProductsBatch(anyList())).thenReturn(products);

        ShipmentDTO shipment = new ShipmentDTO(200L, 99L, "FedEx", "TRACK123", "SHIPPED", null, null, LocalDateTime.now(), null, null, Map.of());
        when(shippingServiceClient.getActiveShipmentForOrder(99L)).thenReturn(shipment);

        Order expectedDelivered = new Order();
        expectedDelivered.setId(99L);
        expectedDelivered.setUserId(42L);
        expectedDelivered.setStatus(OrderStatus.DELIVERED);
        expectedDelivered.setTotalAmount(175.0); // 2*50 + 1*75
        expectedDelivered.setDeliveredAt(LocalDateTime.now());
        when(orderRepository.save(any(Order.class))).thenReturn(expectedDelivered);

        Order result = orderService.deliverOrder(99L);

        assertEquals(175.0, result.getTotalAmount());
        verify(orderEventPublisher).publishOrderCompleted(any(Order.class));
    }

    @Test
    void deliverOrderFailsWhenOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            orderService.deliverOrder(999L);
        });

        assertEquals(404, exception.getStatusCode().value());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
    }

    @Test
    void deliverOrderFailsWhenOrderNotShipped() {
        shippedOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            orderService.deliverOrder(99L);
        });

        assertEquals(400, exception.getStatusCode().value());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
    }

    @Test
    void deliverOrderFailsWhenUserNotActive() {
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        // User is INACTIVE
        UserDTO inactiveUser = new UserDTO(42L, "John Doe", "john@example.com", "555-1234", "CUSTOMER", "INACTIVE", Map.of());
        when(userServiceClient.getUser(42L)).thenReturn(inactiveUser);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            orderService.deliverOrder(99L);
        });

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("User is no longer active"));
        // Order must remain SHIPPED
        assertEquals(OrderStatus.SHIPPED, shippedOrder.getStatus());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliverOrderFailsWhenProductDoesNotExist() {
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        UserDTO activeUser = new UserDTO(42L, "John Doe", "john@example.com", "555-1234", "CUSTOMER", "ACTIVE", Map.of());
        when(userServiceClient.getUser(42L)).thenReturn(activeUser);

        // Only 1 product returned instead of 2
        List<ProductDTO> products = List.of(
                new ProductDTO(101L, "P101", "desc", 50.0, "cat", "brand", 10, "ACTIVE", 4.5, Map.of())
        );
        when(productServiceClient.getProductsBatch(anyList())).thenReturn(products);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            orderService.deliverOrder(99L);
        });

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("Product no longer exists"));
        // Order must remain SHIPPED
        assertEquals(OrderStatus.SHIPPED, shippedOrder.getStatus());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliverOrderFailsWhenNoActiveShipment() {
        when(orderRepository.findById(99L)).thenReturn(Optional.of(shippedOrder));

        UserDTO activeUser = new UserDTO(42L, "John Doe", "john@example.com", "555-1234", "CUSTOMER", "ACTIVE", Map.of());
        when(userServiceClient.getUser(42L)).thenReturn(activeUser);

        List<ProductDTO> products = List.of(
                new ProductDTO(101L, "P101", "desc", 50.0, "cat", "brand", 10, "ACTIVE", 4.5, Map.of()),
                new ProductDTO(102L, "P102", "desc", 75.0, "cat", "brand", 5, "ACTIVE", 4.0, Map.of())
        );
        when(productServiceClient.getProductsBatch(anyList())).thenReturn(products);

        // No active shipment
        when(shippingServiceClient.getActiveShipmentForOrder(99L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            orderService.deliverOrder(99L);
        });

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("No active shipment"));
        // Order must remain SHIPPED
        assertEquals(OrderStatus.SHIPPED, shippedOrder.getStatus());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
        verify(orderRepository, never()).save(any());
    }
}
