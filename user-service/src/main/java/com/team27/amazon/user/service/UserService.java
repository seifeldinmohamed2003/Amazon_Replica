package com.team27.amazon.user.service;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.user.dto.TopBuyerDTO;
import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.dto.UserOrderSummaryDTO;
import com.team27.amazon.user.model.Status;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;
import com.team27.amazon.user.dto.ShippingAddressDTO;
import com.team27.amazon.user.dto.UserProfileDTO;


@Service
public class UserService extends AbstractEventSubject {

    private final UserRepository userRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoEventLogger mongoEventLogger;

    public UserService(UserRepository userRepository,
                       ShippingAddressRepository shippingAddressRepository,
                       PasswordEncoder passwordEncoder,
                       MongoEventLogger mongoEventLogger) {
        this.userRepository = userRepository;
        this.shippingAddressRepository = shippingAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoEventLogger = mongoEventLogger;
        register(mongoEventLogger);
    }

    // ─── User CRUD ───────────────────────────────────────────────

    public User createUser(User user) {
        encodePasswordIfNeeded(user);
        User savedUser = userRepository.save(user);
        notifyObservers("USER_CREATED", userEventPayload(savedUser.getId(), Map.of(
                "email", savedUser.getEmail(),
                "status", savedUser.getStatus() == null ? null : savedUser.getStatus().name()
        )));
        return savedUser;
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
        if (StringUtils.hasText(updated.getPassword())) {
            user.setPassword(encodePassword(updated.getPassword()));
        }
        user.setPhone(updated.getPhone());
        user.setRole(updated.getRole());
        user.setStatus(updated.getStatus());
        user.setPreferences(updated.getPreferences());
        User savedUser = userRepository.save(user);
        notifyObservers("USER_UPDATED", userEventPayload(savedUser.getId(), Map.of(
            "email", savedUser.getEmail(),
            "status", savedUser.getStatus() == null ? null : savedUser.getStatus().name()
        )));
        return savedUser;
    }

    public void deleteUser(Long id) {
        getUserById(id);
        userRepository.deleteById(id);
        notifyObservers("USER_DELETED", userEventPayload(id, Map.of()));
    }

    // ─── ShippingAddress CRUD ─────────────────────────────────────

    public ShippingAddress createAddress(Long userId, ShippingAddress address) {
        User user = getUserById(userId);
        address.setUser(user);
        ShippingAddress savedAddress = shippingAddressRepository.save(address);
        Map<String, Object> eventDetails = addressDetails(savedAddress);
        eventDetails.put("addressId", savedAddress.getId());
        notifyObservers("ADDRESS_CREATED", userEventPayload(userId, eventDetails));
        return savedAddress;
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
        ShippingAddress savedAddress = shippingAddressRepository.save(address);
        Map<String, Object> eventDetails = addressDetails(savedAddress);
        eventDetails.put("addressId", savedAddress.getId());
        notifyObservers("ADDRESS_UPDATED", userEventPayload(userId, eventDetails));
        return savedAddress;
    }

    public void deleteAddress(Long userId, Long addressId) {
        ShippingAddress address = getAddressById(userId, addressId);
        shippingAddressRepository.delete(address);
        notifyObservers("ADDRESS_DELETED", userEventPayload(userId, Map.of("addressId", addressId)));
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

        User savedUser = userRepository.save(user);
        notifyObservers("PREFERENCES_UPDATED", userEventPayload(savedUser.getId(), savedUser.getPreferences() == null ? new HashMap<>() : new HashMap<>(savedUser.getPreferences())));
        return savedUser;
    }

    // S1-F3
    public UserOrderSummaryDTO getUserOrderSummary(Long userId) {
        getUserById(userId); // throws 404 if not found

        Object[] row = userRepository.getUserOrderSummary(userId);

        if (row == null || row.length == 0) {
            // User exists but has no orders
            User user = getUserById(userId);
            return new UserOrderSummaryDTO(userId, user.getName(), 0L, 0L, 0L, 0.0, 0.0);
        }

        return new UserOrderSummaryDTO(
                ((Number) row[0]).longValue(),
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                ((Number) row[5]).doubleValue(),
                ((Number) row[6]).doubleValue()
        );
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
        User savedUser = userRepository.save(user);
        notifyObservers("USER_DEACTIVATED", userEventPayload(savedUser.getId(), Map.of(
            "status", savedUser.getStatus().name()
        )));
        return savedUser;
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

        notifyObservers("DEFAULT_ADDRESS_SET", userEventPayload(userId, Map.of("addressId", addressId, "defaultAddressId", addressId)));

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

    public List<User> findUsersByLanguage(String lang, long minOrders) {
        if (!StringUtils.hasText(lang)) {
            throw new IllegalArgumentException("Language cannot be blank");
        }
        return userRepository.findByLanguageAndMinOrders(lang, minOrders);
    }

    public User changeUserRole(Long id, Role role) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be provided");
        }

        User user = getUserById(id);
        user.setRole(role);
        User savedUser = userRepository.save(user);
        notifyObservers("ROLE_CHANGED", userEventPayload(savedUser.getId(), Map.of(
                "role", savedUser.getRole() == null ? null : savedUser.getRole().name()
        )));
        return savedUser;
    }

    private Map<String, Object> userEventPayload(Long userId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private Map<String, Object> addressDetails(ShippingAddress address) {
        Map<String, Object> details = new HashMap<>();
        if (address == null) {
            return details;
        }

        details.put("label", address.getLabel());
        details.put("city", address.getCity());
        details.put("country", address.getCountry());
        details.put("isDefault", address.getIsDefault());
        return details;
    }

    private void encodePasswordIfNeeded(User user) {
        if (user != null && StringUtils.hasText(user.getPassword()) && !isBCryptHash(user.getPassword())) {
            user.setPassword(encodePassword(user.getPassword()));
        }
    }

    private String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private boolean isBCryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }


}