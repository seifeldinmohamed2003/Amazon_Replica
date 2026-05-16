package com.team27.amazon.order.service;

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

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
import com.team27.amazon.order.repository.OrderRepository;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class OrderServiceEstimateTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private ShippingServiceClient shippingServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void estimateOrderPriceReturnsExpectedValuesForGivenExample() {
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 100.0));
        when(productServiceClient.getProduct(2L)).thenReturn(product(2L, 200.0));

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
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 100.0));
        when(productServiceClient.getProduct(2L)).thenReturn(product(2L, 200.0));

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
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 100.0));

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
        when(productServiceClient.getProduct(999L)).thenThrow(mock(feign.FeignException.NotFound.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.estimateOrderPrice(List.of(new OrderEstimateItemRequestDTO(999L, 1)))
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(orderRepository, userServiceClient, shippingServiceClient);
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

    private ProductDTO product(Long id, Double price) {
        return new ProductDTO(id, "Product " + id, "Description", price, "CATEGORY", "Brand", 10, "ACTIVE", 0.0, Map.of());
    }
}
