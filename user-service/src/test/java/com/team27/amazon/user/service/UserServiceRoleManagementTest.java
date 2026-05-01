package com.team27.amazon.user.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.user.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.model.Status;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.ShippingAddressRepository;
import com.team27.amazon.user.repository.UserRepository;

class UserServiceRoleManagementTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ShippingAddressRepository shippingAddressRepository = mock(ShippingAddressRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MongoEventLogger mongoEventLogger = mock(MongoEventLogger.class);
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();

private final UserService userService = new UserService(
        userRepository,
        shippingAddressRepository,
        passwordEncoder,
        mongoEventLogger,
        objectArrayDtoAdapter
);
    @Test
    void changeUserRoleUpdatesRoleAndSavesUser() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("Admin Target");
        existing.setEmail("target@example.com");
        existing.setPassword("$2a$10$hashedpasswordhashedpasswordhashedpasswor");
        existing.setPhone("01000000000");
        existing.setRole(Role.CUSTOMER);
        existing.setStatus(Status.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.changeUserRole(1L, Role.ADMIN);

        assertEquals(Role.ADMIN, updated.getRole());
    }

    @Test
    void changeUserRoleRejectsNullRole() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.changeUserRole(1L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}