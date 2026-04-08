package com.team27.amazon.user.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShippingAddressRepository shippingAddressRepository;

    public UserService(UserRepository userRepository, ShippingAddressRepository shippingAddressRepository) {
        this.userRepository = userRepository;
        this.shippingAddressRepository = shippingAddressRepository;
    }

    // ─── User CRUD ───────────────────────────────────────────────

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User updated) {
        User user = getUserById(id);
        user.setName(updated.getName());
        user.setEmail(updated.getEmail());
        user.setPassword(updated.getPassword());
        user.setPhone(updated.getPhone());
        user.setRole(updated.getRole());
        user.setStatus(updated.getStatus());
        user.setPreferences(updated.getPreferences());
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        getUserById(id);
        userRepository.deleteById(id);
    }

    // ─── ShippingAddress CRUD ─────────────────────────────────────

    public ShippingAddress createAddress(Long userId, ShippingAddress address) {
        User user = getUserById(userId);
        address.setUser(user);
        return shippingAddressRepository.save(address);
    }

    public ShippingAddress getAddressById(Long userId, Long addressId) {
        getUserById(userId);
        ShippingAddress address = shippingAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address does not belong to this user");
        }
        return address;
    }

    public List<ShippingAddress> getAllAddresses(Long userId) {
        getUserById(userId);
        return shippingAddressRepository.findByUserId(userId);
    }

    public ShippingAddress updateAddress(Long userId, Long addressId, ShippingAddress updated) {
        ShippingAddress address = getAddressById(userId, addressId);
        address.setLabel(updated.getLabel());
        address.setStreetAddress(updated.getStreetAddress());
        address.setCity(updated.getCity());
        address.setCountry(updated.getCountry());
        address.setZipCode(updated.getZipCode());
        address.setIsDefault(updated.getIsDefault());
        address.setMetadata(updated.getMetadata());
        return shippingAddressRepository.save(address);
    }

    public void deleteAddress(Long userId, Long addressId) {
        ShippingAddress address = getAddressById(userId, addressId);
        shippingAddressRepository.delete(address);
    }
}