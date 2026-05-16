package com.team27.amazon.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.team27.amazon.contracts.feign.OrderServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {OrderServiceClient.class, UserServiceClient.class})
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }

}
