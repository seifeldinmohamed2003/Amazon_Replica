package com.team27.amazon.user.service;

import com.team27.amazon.common.events.AuthEvent;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.user.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.user.cache.CacheInvalidationService;
import com.team27.amazon.user.cache.RedisCacheService;
import com.team27.amazon.user.model.Status;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.AuthEventRepository;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceObserverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ShippingAddressRepository shippingAddressRepository = mock(ShippingAddressRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthEventRepository authEventRepository = mock(AuthEventRepository.class);
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();
    private final RedisCacheService redisCacheService = mock(RedisCacheService.class);
    private final CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);

    private final MongoEventLogger mongoEventLogger = new MongoEventLogger(
            com.team27.amazon.common.events.EventType.AUTH,
            new com.team27.amazon.common.events.EventFactory(),
            event -> authEventRepository.save((AuthEvent) event)
    );

    @Test
    void updateUserPreferencesPersistsAuthEventThroughObserver() {
        User user = new User();
        user.setId(55L);
        user.setEmail("user@example.com");
        user.setStatus(Status.ACTIVE);
        user.setPreferences(new HashMap<>(Map.of("theme", "light")));

        when(userRepository.findById(55L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserService service = new UserService(
                userRepository,
                shippingAddressRepository,
                passwordEncoder,
                mongoEventLogger,
                objectArrayDtoAdapter,
                redisCacheService,
                cacheInvalidationService
        );

        service.updateUserPreferences(55L, Map.of("theme", "dark", "language", "en"));

        verify(authEventRepository).save(org.mockito.ArgumentMatchers.argThat(event -> {
            AuthEvent authEvent = (AuthEvent) event;
            assertEquals(55L, authEvent.getUserId());
            assertEquals("USER_UPDATED", authEvent.getAction());
            assertEquals("dark", authEvent.getDetails().get("theme"));
            assertEquals("en", authEvent.getDetails().get("language"));
            return true;
        }));
    }
}