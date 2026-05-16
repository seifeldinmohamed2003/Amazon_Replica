package com.team27.amazon.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.common.events.AuthEvent;
import com.team27.amazon.user.adapter.ActivityCacheAdapter;
import com.team27.amazon.user.dto.ActivityFeedDTO;
import com.team27.amazon.user.repository.AuthEventRepository;
import com.team27.amazon.user.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.contracts.dto.OrderSummaryDTO;
import com.team27.amazon.contracts.dto.UserDTO;
import com.team27.amazon.user.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.user.client.BillingServiceGateway;
import com.team27.amazon.user.client.OrderServiceGateway;
import com.team27.amazon.user.cache.CacheConstants;
import com.team27.amazon.user.cache.CacheInvalidationService;
import com.team27.amazon.user.cache.CacheKeyBuilder;
import com.team27.amazon.user.cache.RedisCacheService;
import com.team27.amazon.user.dto.ShippingAddressDTO;
import com.team27.amazon.user.dto.TopBuyerDTO;
import com.team27.amazon.user.dto.UserOrderSummaryDTO;
import com.team27.amazon.user.dto.UserProfileDTO;
import com.team27.amazon.user.dto.UserProfileDTOBuilder;
import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.model.ShippingAddress;
import com.team27.amazon.user.model.Status;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;

@Service
public class UserService extends AbstractEventSubject {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoEventLogger mongoEventLogger;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;
    private final RedisCacheService redisCacheService;
    private final CacheInvalidationService cacheInvalidationService;

    private final OrderServiceGateway orderServiceGateway;
    private final BillingServiceGateway billingServiceGateway;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    // S1-F12 dependencies
    private final AuthEventRepository authEventRepository;
    private final ActivityCacheAdapter cacheAdapter;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    // ─── Main constructor (used by Spring) ───────────────────────
    @Autowired
    public UserService(UserRepository userRepository,
                       ShippingAddressRepository shippingAddressRepository,
                       PasswordEncoder passwordEncoder,
                       MongoEventLogger mongoEventLogger,
                       ObjectArrayDtoAdapter objectArrayDtoAdapter,
                       RedisCacheService redisCacheService,
                       CacheInvalidationService cacheInvalidationService,
                       AuthEventRepository authEventRepository,
                       ActivityCacheAdapter cacheAdapter,
                       ObjectMapper objectMapper,
                       JwtService jwtService,
                       OrderServiceGateway orderServiceGateway,
                       BillingServiceGateway billingServiceGateway,
                       org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.shippingAddressRepository = shippingAddressRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoEventLogger = mongoEventLogger;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
        this.redisCacheService = redisCacheService;
        this.cacheInvalidationService = cacheInvalidationService;
        this.authEventRepository = authEventRepository;
        this.cacheAdapter = cacheAdapter;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.orderServiceGateway = orderServiceGateway;
        this.billingServiceGateway = billingServiceGateway;
        this.rabbitTemplate = rabbitTemplate;

        register(mongoEventLogger);
    }

    // ─── Test constructor for older/unit tests ─────────────────────
    public UserService(UserRepository userRepository,
                       ShippingAddressRepository shippingAddressRepository,
                       PasswordEncoder passwordEncoder,
                       MongoEventLogger mongoEventLogger,
                       ObjectArrayDtoAdapter objectArrayDtoAdapter,
                       RedisCacheService redisCacheService,
                       CacheInvalidationService cacheInvalidationService) {
        this(userRepository,
                shippingAddressRepository,
                passwordEncoder,
                mongoEventLogger,
                objectArrayDtoAdapter,
                redisCacheService,
                cacheInvalidationService,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
    // ─── User CRUD ───────────────────────────────────────────────

    public User createUser(User user) {
        encodePasswordIfNeeded(user);

        User savedUser = userRepository.save(user);

        notifyObservers("USER_CREATED", userEventPayload(savedUser.getId(), Map.of(
                "email", savedUser.getEmail(),
                "status", savedUser.getStatus() == null ? null : savedUser.getStatus().name()
        )));

        // S1-F10: publish user.registered
        log.info("Publishing user.registered for userId={} email={}", savedUser.getId(), savedUser.getEmail());
        rabbitTemplate.convertAndSend(
                com.team27.amazon.contracts.constants.EventExchanges.USER_EVENTS,
                com.team27.amazon.contracts.constants.EventRoutingKeys.USER_REGISTERED,
                Map.of(
                        "userId", savedUser.getId(),
                        "email", savedUser.getEmail(),
                        "role", savedUser.getRole() == null ? null : savedUser.getRole().name()
                )
        );

        cacheInvalidationService.invalidateAllUserServiceFeatureCaches();

        return savedUser;
    }

    public User getUserById(Long id) {
        String cacheKey = CacheKeyBuilder.entityKey(CacheConstants.ENTITY_USER, id);

        return redisCacheService.getOrCompute(
                cacheKey,
                User.class,
                CacheConstants.TTL_ENTITY_DETAIL,
                () -> getUserByIdFromDatabase(id)
        );
    }

    public UserDTO getUserAsDTO(Long id) {
        User user = getUserById(id);
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getPreferences()
        );
    }

    private User getUserByIdFromDatabase(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User updated) {
        User user = getUserByIdFromDatabase(id);

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

        cacheInvalidationService.invalidateUserWriteCaches(savedUser.getId());

        return savedUser;
    }

    public void deleteUser(Long id) {
        getUserByIdFromDatabase(id);

        userRepository.deleteById(id);

        notifyObservers("USER_DELETED", userEventPayload(id, Map.of()));

        cacheInvalidationService.invalidateUserWriteCaches(id);
    }

    // ─── ShippingAddress CRUD ─────────────────────────────────────

    public ShippingAddress createAddress(Long userId, ShippingAddress address) {
        User user = getUserByIdFromDatabase(userId);

        address.setUser(user);

        ShippingAddress savedAddress = shippingAddressRepository.save(address);

        Map<String, Object> eventDetails = addressDetails(savedAddress);
        eventDetails.put("addressId", savedAddress.getId());

        notifyObservers("ADDRESS_CREATED", userEventPayload(userId, eventDetails));

        cacheInvalidationService.invalidateUserDetail(userId);
        cacheInvalidationService.invalidateAllUserServiceFeatureCaches();

        return savedAddress;
    }

    public ShippingAddress getAddressById(Long userId, Long addressId) {
        getUserByIdFromDatabase(userId);

        String cacheKey = CacheKeyBuilder.entityKey(
                CacheConstants.ENTITY_SHIPPING_ADDRESS,
                addressId
        );

        ShippingAddress address = redisCacheService.getOrCompute(
                cacheKey,
                ShippingAddress.class,
                CacheConstants.TTL_ENTITY_DETAIL,
                () -> getAddressByIdFromDatabase(addressId)
        );

        if (address.getUser() == null || !address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Shipping address not found for this user"
            );
        }

        return address;
    }

    public com.team27.amazon.contracts.dto.ShippingAddressDTO getShippingAddressAsDTO(Long userId, Long addressId) {
        getUserByIdFromDatabase(userId);
        ShippingAddress address = getAddressByIdFromDatabase(addressId);
        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        return new com.team27.amazon.contracts.dto.ShippingAddressDTO(
                address.getId(),
                userId,
                address.getStreetAddress(),
                address.getCity(),
                address.getLabel(),
                address.getZipCode(),
                address.getCountry()
        );
    }

    private ShippingAddress getAddressByIdFromDatabase(Long addressId) {
        return shippingAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    public List<ShippingAddress> getAllAddresses(Long userId) {
        getUserByIdFromDatabase(userId);
        return shippingAddressRepository.findByUserId(userId);
    }

    private ShippingAddress getAddressByIdForWrite(Long userId, Long addressId) {
        getUserByIdFromDatabase(userId);

        ShippingAddress address = getAddressByIdFromDatabase(addressId);

        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address does not belong to this user");
        }

        return address;
    }

    public ShippingAddress updateAddress(Long userId, Long addressId, ShippingAddress updated) {
        ShippingAddress address = getAddressByIdForWrite(userId, addressId);

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

        cacheInvalidationService.invalidateShippingAddressWriteCaches(savedAddress.getId(), userId);

        return savedAddress;
    }

    public void deleteAddress(Long userId, Long addressId) {
        ShippingAddress address = getAddressByIdForWrite(userId, addressId);

        shippingAddressRepository.delete(address);

        notifyObservers("ADDRESS_DELETED", userEventPayload(userId, Map.of("addressId", addressId)));

        cacheInvalidationService.invalidateShippingAddressWriteCaches(addressId, userId);
    }

    // S1-F1
    public List<User> searchUsers(String name, String email, String role) {
        String nameParam = StringUtils.hasText(name) ? name.trim() : null;
        String emailParam = StringUtils.hasText(email) ? email.trim() : null;
        String roleParam = StringUtils.hasText(role) ? role.trim() : null;

        String cacheKey = CacheKeyBuilder.featureKeyFromParams(
                CacheConstants.S1_F1,
                Map.of(
                        "name", nameParam == null ? "" : nameParam,
                        "email", emailParam == null ? "" : emailParam,
                        "role", roleParam == null ? "" : roleParam
                )
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                new TypeReference<List<User>>() {},
                CacheConstants.TTL_F1_SEARCH,
                () -> userRepository.searchUsers(nameParam, emailParam, roleParam)
        );
    }

    // S1-F2
    public User updateUserPreferences(Long id, Map<String, Object> incomingPreferences) {
        User user = getUserByIdFromDatabase(id);

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

        notifyObservers("USER_UPDATED", userEventPayload(
                savedUser.getId(),
                savedUser.getPreferences() == null
                        ? new HashMap<>()
                        : new HashMap<>(savedUser.getPreferences())
        ));

        cacheInvalidationService.invalidateUserWriteCaches(savedUser.getId());

        return savedUser;
    }

    // S1-F3
    public UserOrderSummaryDTO getUserOrderSummary(Long userId) {
        String cacheKey = CacheKeyBuilder.featureKeyDirect(
                CacheConstants.S1_F3,
                String.valueOf(userId)
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                UserOrderSummaryDTO.class,
                CacheConstants.TTL_F3_DTO,
                () -> getUserOrderSummaryFromOrderService(userId)
        );
    }

    private UserOrderSummaryDTO getUserOrderSummaryFromOrderService(Long userId) {
        User user = getUserByIdFromDatabase(userId);
        OrderSummaryDTO summary = orderServiceGateway.getUserOrderSummary(userId);
        return buildUserOrderSummaryDTO(user, summary);
    }

    private UserOrderSummaryDTO buildUserOrderSummaryDTO(User user, OrderSummaryDTO summary) {
        return UserOrderSummaryDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .totalOrders(summary.totalOrders())
                .completedOrders(summary.completedOrders())
                .cancelledOrders(summary.cancelledOrders())
                .totalSpent(summary.totalSpent())
                .averageOrderValue(summary.averageOrderValue())
                .build();
    }

    // S1-F4
    @Transactional
    public User deactivateUser(Long id) {
        User user = getUserByIdFromDatabase(id);

        int activeOrderCount = orderServiceGateway.getActiveOrderCount(id);
        if (activeOrderCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User has active orders"
            );
        }
        user.setStatus(Status.DEACTIVATED);

        User savedUser = userRepository.save(user);

        notifyObservers("USER_DEACTIVATED", userEventPayload(savedUser.getId(), Map.of(
                "status", savedUser.getStatus().name()
        )));

        log.info("Publishing user.deactivated for userId={}", savedUser.getId());
        rabbitTemplate.convertAndSend(
                com.team27.amazon.contracts.constants.EventExchanges.USER_EVENTS,
                com.team27.amazon.contracts.constants.EventRoutingKeys.USER_DEACTIVATED,
                Map.of("userId", savedUser.getId())
        );

        cacheInvalidationService.invalidateUserWriteCaches(savedUser.getId());

        return savedUser;
    }

    // S1-F5
    public List<User> findUsersByPreference(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Preference key and value must not be blank"
            );
        }

        String keyParam = key.trim();
        String valueParam = value.trim();

        String cacheKey = CacheKeyBuilder.featureKeyFromParams(
                CacheConstants.S1_F5,
                Map.of(
                        "key", keyParam,
                        "value", valueParam
                )
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                new TypeReference<List<User>>() {},
                CacheConstants.TTL_F5_JSONB,
                () -> userRepository.findUsersByPreference(keyParam, valueParam)
        );
    }

    // S1-F6
    public List<TopBuyerDTO> getTopBuyers(LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than zero");
        }

        String cacheKey = CacheKeyBuilder.featureKeyFromParams(
                CacheConstants.S1_F6,
                Map.of(
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString(),
                        "limit", limit
                )
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                new TypeReference<List<TopBuyerDTO>>() {},
                CacheConstants.TTL_F6_REPORT,
                () -> getTopBuyersFromBillingService(startDate, endDate, limit)
        );
    }

    private List<TopBuyerDTO> getTopBuyersFromBillingService(LocalDate startDate, LocalDate endDate, int limit) {
        String start = startDate.toString();
        String end = endDate.toString();

        List<User> users = userRepository.findAll();

        return users.stream()
                .map(user -> {
                    java.math.BigDecimal total = billingServiceGateway.getUserTransactionTotal(user.getId(), start, end);
                    long count = billingServiceGateway.getUserOrderCount(user.getId(), start, end);
                    return TopBuyerDTO.builder()
                            .userId(user.getId())
                            .name(user.getName())
                            .totalSpent(total.doubleValue())
                            .orderCount(count)
                            .build();
                })
                .filter(dto -> dto.getTotalSpent() > 0)
                .sorted((a, b) -> Double.compare(b.getTotalSpent(), a.getTotalSpent()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    // S1-F7
    @Transactional
    public User setDefaultAddress(Long userId, Long addressId) {
        User user = getUserByIdFromDatabase(userId);

        ShippingAddress target = getAddressByIdForWrite(userId, addressId);

        List<ShippingAddress> addresses = user.getShippingAddresses();

        if (addresses == null || addresses.isEmpty()) {
            addresses = shippingAddressRepository.findByUserId(userId);
        }

        for (ShippingAddress addr : addresses) {
            addr.setIsDefault(false);
        }

        target.setIsDefault(true);

        shippingAddressRepository.saveAll(addresses);

        notifyObservers("DEFAULT_ADDRESS_SET", userEventPayload(userId, Map.of(
                "addressId", addressId,
                "defaultAddressId", addressId
        )));

        cacheInvalidationService.invalidateUserDetail(userId);

        for (ShippingAddress addr : addresses) {
            cacheInvalidationService.invalidateShippingAddressDetail(addr.getId());
        }

        cacheInvalidationService.invalidateAllUserServiceFeatureCaches();

        return user;
    }

    // S1-F8
    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(Long userId) {
        String cacheKey = CacheKeyBuilder.featureKeyDirect(
                CacheConstants.S1_F8,
                String.valueOf(userId)
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                UserProfileDTO.class,
                CacheConstants.TTL_F8_RELATIONSHIP,
                () -> getUserProfileFromDatabase(userId)
        );
    }

    private UserProfileDTO getUserProfileFromDatabase(Long userId) {
        User user = getUserByIdFromDatabase(userId);

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
        return UserProfileDTOBuilder.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .shippingAddresses(addresses)
                .totalAddresses(addresses.size())
                .build();
    }

    // S1-F9
    public List<User> findUsersByLanguage(String lang, long minOrders) {
        if (!StringUtils.hasText(lang)) {
            throw new IllegalArgumentException("Language cannot be blank");
        }

        String langParam = lang.trim();

        String cacheKey = CacheKeyBuilder.featureKeyFromParams(
                CacheConstants.S1_F9,
                Map.of(
                        "lang", langParam,
                        "minOrders", minOrders
                )
        );

        return redisCacheService.getOrCompute(
                cacheKey,
                new TypeReference<List<User>>() {},
                CacheConstants.TTL_F9_COMBINED,
                () -> findUsersByLanguageFromOrderService(langParam, minOrders)
        );
    }

    private List<User> findUsersByLanguageFromOrderService(String lang, long minOrders) {
        List<User> candidates = userRepository.findUsersByPreference("language", lang);
        return candidates.stream()
                .filter(user -> orderServiceGateway.getTotalOrderCount(user.getId()) >= minOrders)
                .collect(java.util.stream.Collectors.toList());
    }

    // CC-2
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

        cacheInvalidationService.invalidateUserWriteCaches(savedUser.getId());

        return savedUser;
    }

    // S1-F12
    public ActivityFeedDTO getUserActivityFeed(Long userId, int page, int size, String token) {

        // 1. Validate JWT
        if (!jwtService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token");
        }

        // 2. Ownership check
        Long callerUid = jwtService.extractUserId(token);
        String callerRole = jwtService.extractRole(token);
        if (!callerUid.equals(userId) && !"ADMIN".equals(callerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // 3. Find user in PostgreSQL — throws 404 if not found
        getUserById(userId);

        // 4. Cap size at 100
        size = Math.min(size, 100);

        // 5. Check Redis cache via adapter
        String cacheKey = "activity_feed:" + userId + ":" + page + ":" + size;
        Optional<String> cached = cacheAdapter.get(cacheKey);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), ActivityFeedDTO.class);
            } catch (Exception ignored) {}
        }

        // 6. Query MongoDB
        Pageable pageable = PageRequest.of(page, size);
        Page<AuthEvent> events = authEventRepository
                .findByUserIdOrderByTimestampDesc(userId, pageable);

        // 7. Build response using Builder pattern
        List<ActivityFeedDTO.ActivityEventDTO> content = events.getContent().stream()
                .map(e -> new ActivityFeedDTO.ActivityEventDTO.Builder()
                        .action(e.getAction())
                        .timestamp(e.getTimestamp().toString())
                        .details(e.getDetails())
                        .build())
                .toList();

        ActivityFeedDTO result = new ActivityFeedDTO.Builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(events.getTotalElements())
                .build();

        // 8. Cache for 5 minutes via adapter
        try {
            cacheAdapter.set(cacheKey, objectMapper.writeValueAsString(result), 5);
        } catch (Exception ignored) {}

        return result;
    }

    // ─── Private helpers ─────────────────────────────────────────

    private Map<String, Object> userEventPayload(Long userId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private Map<String, Object> addressDetails(ShippingAddress address) {
        Map<String, Object> details = new HashMap<>();
        if (address == null) return details;
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
        return value != null &&
                (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}