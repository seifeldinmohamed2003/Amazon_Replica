package com.team27.amazon.user;

import com.team27.amazon.contracts.feign.BillingServiceClient;
import com.team27.amazon.contracts.feign.OrderServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {
        OrderServiceClient.class,
        BillingServiceClient.class
})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}