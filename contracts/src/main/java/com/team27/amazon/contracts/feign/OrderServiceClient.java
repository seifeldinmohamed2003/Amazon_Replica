package com.team27.amazon.contracts.feign;

import com.team27.amazon.contracts.dto.OrderDTO;
import com.team27.amazon.contracts.dto.OrderItemDTO;
import com.team27.amazon.contracts.dto.OrderSummaryDTO;
import com.team27.amazon.contracts.dto.ProductSalesAggregateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "order-service", url = "${FEIGN_ORDER_SERVICE_URL:http://order-service:8080}")
public interface OrderServiceClient {

    @GetMapping("/api/orders/{orderId}")
    OrderDTO getOrder(@PathVariable("orderId") Long orderId);

    @GetMapping("/api/orders/{orderId}/items")
    List<OrderItemDTO> getOrderItems(@PathVariable("orderId") Long orderId);

    @GetMapping("/api/orders/user/{userId}/summary")
    OrderSummaryDTO getUserOrderSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/orders/user/{userId}/active-count")
    int getActiveOrderCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/orders/user/{userId}/count")
    long getTotalOrderCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/orders/product/{productId}/sales")
    ProductSalesAggregateDTO getProductSales(
            @PathVariable("productId") Long productId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    @GetMapping("/api/orders/product/{productId}/pending-count")
    int getPendingOrderCountForProduct(@PathVariable("productId") Long productId);

    @GetMapping("/api/orders/product/{productId}/units-sold")
    long getUnitsSold(@PathVariable("productId") Long productId);

    @GetMapping("/api/orders/product/{productId}/recent-sales-count")
    int getRecentSalesCount(
            @PathVariable("productId") Long productId,
            @RequestParam("days") int days
    );

    @GetMapping("/api/orders/user/{userId}/has-purchased/{productId}")
    boolean hasUserPurchasedProduct(
            @PathVariable("userId") Long userId,
            @PathVariable("productId") Long productId
    );
}
