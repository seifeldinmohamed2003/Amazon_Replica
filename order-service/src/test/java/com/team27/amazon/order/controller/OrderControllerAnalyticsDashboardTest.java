package com.team27.amazon.order.controller;

import com.team27.amazon.order.dto.OrderAnalyticsDashboardDTO;
import com.team27.amazon.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerAnalyticsDashboardTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void invalidDateRangeReturnsBadRequest() throws Exception {
        // Arrange: mock service to throw Bad Request when invalid range provided
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range"))
                .when(orderService).getOrderAnalyticsDashboard(
                        org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026,4,1)),
                        org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026,3,1))
                );

        // Act & Assert
        mockMvc.perform(get("/api/orders/analytics/dashboard")
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }
}
