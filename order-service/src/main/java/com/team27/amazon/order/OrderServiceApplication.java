package com.team27.amazon.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import com.team27.amazon.contracts.feign.BillingServiceClient;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {
        UserServiceClient.class,
        ProductServiceClient.class,
        ShippingServiceClient.class,
        BillingServiceClient.class
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
