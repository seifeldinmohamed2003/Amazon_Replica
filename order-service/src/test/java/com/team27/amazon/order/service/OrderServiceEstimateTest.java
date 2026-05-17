package com.team27.amazon.order.service;

import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;
import com.team27.amazon.order.repository.ShipmentJdbcRepository;
import com.team27.amazon.order.repository.ShippingAddressJdbcRepository;
import com.team27.amazon.order.repository.TransactionJdbcRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceEstimateTest {

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
    private ProductServiceClient productServiceClient;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void estimateOrderPriceReturnsExpectedValuesForGivenExample() {
        when(productServiceClient.getProductsBatch(java.util.List.of(1L, 2L)))
            .thenReturn(java.util.List.of(
                mockProductDTO(1L, 100.0),
                mockProductDTO(2L, 200.0)
            ));

        List<OrderEstimateItemRequestDTO> items = List.of(
                new OrderEstimateItemRequestDTO(1L, 2),
                new OrderEstimateItemRequestDTO(2L, 1)
        );

        OrderEstimateDTO estimate = orderService.estimateOrderPrice(items);

        assertEquals(3, estimate.getItemCount());
        assertEquals(400.0, estimate.getSubtotal());
        assertEquals(0.0, estimate.getDiscountApplied());
        assertEquals(50.0, estimate.getShippingCost());
        assertEquals(450.0, estimate.getEstimatedTotal());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void estimateOrderPriceAppliesFivePercentDiscountForTenItems() {
        when(productServiceClient.getProductsBatch(java.util.List.of(1L, 2L)))
            .thenReturn(java.util.List.of(
                mockProductDTO(1L, 100.0),
                mockProductDTO(2L, 200.0)
            ));

        List<OrderEstimateItemRequestDTO> items = List.of(
                new OrderEstimateItemRequestDTO(1L, 4),
                new OrderEstimateItemRequestDTO(2L, 6)
        );

        OrderEstimateDTO estimate = orderService.estimateOrderPrice(items);

        assertEquals(10, estimate.getItemCount());
        assertEquals(1600.0, estimate.getSubtotal());
        assertEquals(5.0, estimate.getDiscountApplied());
        assertEquals(0.0, estimate.getShippingCost());
        assertEquals(1520.0, estimate.getEstimatedTotal());
    }

    @Test
    void estimateOrderPriceAppliesTenPercentDiscountAboveFifteenItems() {
        when(productServiceClient.getProductsBatch(java.util.List.of(1L)))
            .thenReturn(java.util.List.of(
                mockProductDTO(1L, 100.0)
            ));

        List<OrderEstimateItemRequestDTO> items = List.of(
                new OrderEstimateItemRequestDTO(1L, 16)
        );

        OrderEstimateDTO estimate = orderService.estimateOrderPrice(items);

        assertEquals(16, estimate.getItemCount());
        assertEquals(1600.0, estimate.getSubtotal());
        assertEquals(10.0, estimate.getDiscountApplied());
        assertEquals(0.0, estimate.getShippingCost());
        assertEquals(1440.0, estimate.getEstimatedTotal());
    }

    @Test
    void estimateOrderPriceReturnsNotFoundWhenProductDoesNotExist() {
        when(productServiceClient.getProductsBatch(java.util.List.of(999L))).thenReturn(java.util.List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.estimateOrderPrice(List.of(new OrderEstimateItemRequestDTO(999L, 1)))
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(orderRepository, shipmentJdbcRepository, shippingAddressJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void estimateOrderPriceReturnsBadRequestWhenItemsEmpty() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.estimateOrderPrice(List.of())
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productServiceClient);
    }

    private com.team27.amazon.contracts.dto.ProductDTO mockProductDTO(Long id, Double price) {
        return new com.team27.amazon.contracts.dto.ProductDTO(
                id,
                "P" + id,
                "desc",
                price,
                "CAT",
                "BRAND",
                100,
                "ACTIVE",
                4.5,
                java.util.Map.of()
        );
    }
}
