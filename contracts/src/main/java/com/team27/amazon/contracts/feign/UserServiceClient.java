package com.team27.amazon.contracts.feign;

import com.team27.amazon.contracts.dto.ShippingAddressDTO;
import com.team27.amazon.contracts.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable("id") Long id);

    @GetMapping("/api/users/{userId}/addresses/{addressId}")
    ShippingAddressDTO getShippingAddress(
            @PathVariable("userId") Long userId,
            @PathVariable("addressId") Long addressId
    );
}
