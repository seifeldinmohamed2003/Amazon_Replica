package com.team27.amazon.user.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.team27.amazon.user.dto.TopBuyerDTO;
import com.team27.amazon.user.dto.RoleUpdateRequest;
import com.team27.amazon.user.dto.UserOrderSummaryDTO;
import com.team27.amazon.user.dto.UserProfileDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id,
                                              @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(userService.changeUserRole(id, request.getRole()));
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

    // S1-F1
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(userService.searchUsers(name, email, role));
    }

    // S1-F2
    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updateUserPreferences(
            @PathVariable Long id,
            @RequestBody Map<String, Object> preferences) {
        return ResponseEntity.ok(userService.updateUserPreferences(id, preferences));
    }

    // S1-F3
    @GetMapping("/{id}/order-summary")
    public ResponseEntity<UserOrderSummaryDTO> getUserOrderSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserOrderSummary(id));
    }

    //S1-F4
    @PutMapping("/{id}/deactivate")
    public User deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    //S1-F5
    @GetMapping("/preferences/search")
    public List<User> findUsersByPreference(
            @RequestParam String key,
            @RequestParam String value
    ) {
        return userService.findUsersByPreference(key, value);
    }

    //S1-F6
    @GetMapping("/reports/top-buyers")
    public List<TopBuyerDTO> getTopBuyers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int limit
    ) {
        return userService.getTopBuyers(startDate, endDate, limit);
    }

    //S1-F7
    @PutMapping("/{userId}/addresses/{addressId}/default")
    public ResponseEntity<User> setDefaultAddress(
            @PathVariable Long userId,
            @PathVariable Long addressId) {

        return ResponseEntity.ok(
                userService.setDefaultAddress(userId, addressId)
        );
    }

    //S1-F8
    @GetMapping("/{id}/profile")
    public UserProfileDTO getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @GetMapping("/language")
    public ResponseEntity<List<User>> getUsersByLanguage(
            @RequestParam String lang,
            @RequestParam(name = "minOrders", defaultValue = "0") long minOrders
    ) {
        if (lang == null || lang.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<User> users = userService.findUsersByLanguage(lang, minOrders);
        return ResponseEntity.ok(users);
    }


}