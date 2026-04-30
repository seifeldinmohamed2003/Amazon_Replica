package com.team27.amazon.user.service;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.user.config.JwtConfigurationManager;
import com.team27.amazon.user.dto.AuthResponse;
import com.team27.amazon.user.dto.LoginRequest;
import com.team27.amazon.user.dto.RegisterRequest;
import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.model.Status;
import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.UserRepository;
import com.team27.amazon.user.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService extends AbstractEventSubject {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MongoEventLogger mongoEventLogger;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MongoEventLogger mongoEventLogger
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mongoEventLogger = mongoEventLogger;
        register(mongoEventLogger);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        notifyObservers("LOGGED_IN", authEventPayload(user, Map.of(
                "email", user.getEmail()
        )));

        String token = jwtService.generateToken(user);
        long expiresIn = JwtConfigurationManager.getInstance().getExpirationMs();

        return new AuthResponse(token, expiresIn);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already exists");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(Status.ACTIVE);
        user.setPreferences(
                request.getPreferences() == null
                        ? new HashMap<>()
                        : new HashMap<>(request.getPreferences())
        );

        User savedUser = userRepository.save(user);

        notifyObservers("REGISTERED", authEventPayload(savedUser, Map.of(
                "email", savedUser.getEmail(),
                "role", savedUser.getRole().name(),
                "status", savedUser.getStatus().name()
        )));

        String token = jwtService.generateToken(savedUser);
        long expiresIn = JwtConfigurationManager.getInstance().getExpirationMs();

        return new AuthResponse(token, expiresIn);
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }

        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        if (!StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        if (!StringUtils.hasText(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone is required");
        }

        if (request.getPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
    }

    private Map<String, Object> authEventPayload(User user, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }
}