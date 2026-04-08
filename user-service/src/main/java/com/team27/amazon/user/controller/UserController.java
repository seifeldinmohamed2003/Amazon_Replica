package com.team27.amazon.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ─── User CRUD ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ─── ShippingAddress CRUD ─────────────────────────────────────

    @PostMapping("/{userId}/addresses")
    public ResponseEntity<ShippingAddress> createAddress(@PathVariable Long userId,
                                                          @RequestBody ShippingAddress address) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createAddress(userId, address));
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

    //S1-F4
    @PutMapping("/{id}/deactivate")
    public User deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }
}