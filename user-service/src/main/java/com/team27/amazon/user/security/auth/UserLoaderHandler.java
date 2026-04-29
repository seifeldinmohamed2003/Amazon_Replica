package com.team27.amazon.user.security.auth;

import com.team27.amazon.user.model.User;
import com.team27.amazon.user.repository.UserRepository;
import com.team27.amazon.user.security.JwtService;
import org.springframework.http.HttpStatus;

public class UserLoaderHandler extends AuthHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public UserLoaderHandler(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        Long userId = jwtService.extractUserId(context.getToken());
        if (userId == null) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "Token is missing the uid claim");
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "User not found");
        }

        context.setUser(user);
        return callNext(context);
    }
}