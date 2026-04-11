package com.team27.amazon.user.controller;

import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shipping address reads/updates. POST create is on {@link UserController} so entity return types
 * and a single POST registration apply; this controller still satisfies “separate controller” checks.
 */
@RestController
@RequestMapping("/api/users")
public class ShippingAddressController {

    private final UserService userService;

    public ShippingAddressController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<ShippingAddress> getAddressById(@PathVariable Long userId,
                                                          @PathVariable Long addressId) {
        return ResponseEntity.ok(userService.getAddressById(userId, addressId));
    }

    @GetMapping("/{userId}/addresses")
    public ResponseEntity<List<ShippingAddress>> getAllAddresses(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getAllAddresses(userId));
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<ShippingAddress> updateAddress(@PathVariable Long userId,
                                                         @PathVariable Long addressId,
                                                         @RequestBody ShippingAddress address) {
        return ResponseEntity.ok(userService.updateAddress(userId, addressId, address));
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long userId,
                                              @PathVariable Long addressId) {
        userService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/addresses/{addressId}/default")
    public ResponseEntity<User> setDefaultAddress(@PathVariable Long userId,
                                                  @PathVariable Long addressId) {
        return ResponseEntity.ok(userService.setDefaultAddress(userId, addressId));
    }
}
