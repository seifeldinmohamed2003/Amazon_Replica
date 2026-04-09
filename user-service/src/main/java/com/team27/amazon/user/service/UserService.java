package com.team27.amazon.user.service;



import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import com.team27.amazon.user.dto.TopBuyerDTO;
import com.team27.amazon.user.model.Status;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;
import com.team27.amazon.user.dto.ShippingAddressDTO;
import com.team27.amazon.user.dto.UserProfileDTO;


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

    // S1-F1
    public List<User> searchUsers(String name, String email, String role) {
        String nameParam  = (name  != null && !name.trim().isEmpty())  ? name.trim()  : null;
        String emailParam = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        String roleParam  = (role  != null && !role.trim().isEmpty())  ? role.trim()  : null;

        return userRepository.searchUsers(nameParam, emailParam, roleParam);
    }

    // S1-F2
    public User updateUserPreferences(Long id, Map<String, Object> incomingPreferences) {
        User user = getUserById(id);

        Map<String, Object> existing = user.getPreferences();

        if (existing == null) {
            user.setPreferences(incomingPreferences);
        } else {
            for (Map.Entry<String, Object> entry : incomingPreferences.entrySet()) {
                existing.put(entry.getKey(), entry.getValue());
            }
            user.setPreferences(existing);
        }

        return userRepository.save(user);
    }

    //S1-F4
    @Transactional
    public User deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"
                ));

        boolean hasActiveOrders = userRepository.existsActiveOrdersByUserId(id);

        if (hasActiveOrders) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User has active orders and cannot be deactivated"
            );
        }

        user.setStatus(Status.DEACTIVATED);
        return userRepository.save(user);
    }

    //S1-F5
    public List<User> findUsersByPreference(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Preference key and value must not be blank"
            );
        }

        return userRepository.findUsersByPreference(key.trim(), value.trim());
    }

    //S1-F6
    public List<TopBuyerDTO> getTopBuyers(LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range"
            );
        }

        if (limit <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Limit must be greater than zero"
            );
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateExclusive = endDate.plusDays(1).atStartOfDay();

        List<Object[]> rows = userRepository.findTopBuyersByDateRange(
                startDateTime,
                endDateExclusive,
                limit
        );

        List<TopBuyerDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            Double totalSpent = ((Number) row[2]).doubleValue();
            Long orderCount = ((Number) row[3]).longValue();

            result.add(new TopBuyerDTO(userId, name, totalSpent, orderCount));
        }

        return result;
    }

    // S1-F7
    @Transactional
    public User setDefaultAddress(Long userId, Long addressId) {

        // 1. Validate user (404)
        User user = getUserById(userId);

        // 2. Validate address + ownership (404 / 400)
        ShippingAddress target = getAddressById(userId, addressId);

        // 3. IMPORTANT: Use user's collection (cleaner + consistent)
        List<ShippingAddress> addresses = user.getShippingAddresses();

        // If LAZY not loaded, force fetch (safe fallback)
        if (addresses == null || addresses.isEmpty()) {
            addresses = shippingAddressRepository.findByUserId(userId);
        }

        // 4. Reset all
        for (ShippingAddress addr : addresses) {
            addr.setIsDefault(false);
        }

        // 5. Set target
        target.setIsDefault(true);

        // 6. Save (ensures persistence)
        shippingAddressRepository.saveAll(addresses);

        return user;
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<ShippingAddressDTO> addresses = user.getShippingAddresses().stream()
                .map(addr -> new ShippingAddressDTO(
                        addr.getLabel(),
                        addr.getStreetAddress(),
                        addr.getCity(),
                        addr.getCountry(),
                        addr.getZipCode(),
                        addr.getIsDefault(),
                        addr.getMetadata()
                ))
                .toList();

        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getPreferences(),
                addresses,
                addresses.size()
        );
    }

}