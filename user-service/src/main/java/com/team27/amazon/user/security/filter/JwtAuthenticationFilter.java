package com.team27.amazon.user.security.filter;

import java.io.IOException;
import java.util.List;

import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.security.JwtService;
import com.team27.amazon.user.security.auth.AuthContext;
import com.team27.amazon.user.security.auth.AuthHandler;
import com.team27.amazon.user.security.auth.AuthResult;
import com.team27.amazon.user.security.auth.RoleAuthorizationHandler;
import com.team27.amazon.user.security.auth.SignatureValidationHandler;
import com.team27.amazon.user.security.auth.TokenExtractionHandler;
import com.team27.amazon.user.security.auth.UserLoaderHandler;
import com.team27.amazon.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        return "/error".equals(path)
                || "/api/users/health".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/register/".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/login/".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthContext context = new AuthContext(request);
        context.setRequiredRole(determineRequiredRole(request));

        AuthHandler head = buildHandlerChain();
        AuthResult result = head.handle(context);

        if (!result.isSuccess()) {
            response.sendError(result.getStatus(), result.getMessage());
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                context.getUser(),
                context.getToken(),
                List.of(new SimpleGrantedAuthority("ROLE_" + context.getUser().getRole().name()))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private AuthHandler buildHandlerChain() {
        AuthHandler tokenExtractionHandler = new TokenExtractionHandler();
        AuthHandler signatureValidationHandler = new SignatureValidationHandler(jwtService);
        AuthHandler userLoaderHandler = new UserLoaderHandler(jwtService, userRepository);
        AuthHandler roleAuthorizationHandler = new RoleAuthorizationHandler();

        tokenExtractionHandler.setNext(signatureValidationHandler);
        signatureValidationHandler.setNext(userLoaderHandler);
        userLoaderHandler.setNext(roleAuthorizationHandler);
        return tokenExtractionHandler;
    }

    private Role determineRequiredRole(HttpServletRequest request) {
        if (HttpMethod.PUT.matches(request.getMethod()) && request.getRequestURI().matches("^/api/users/\\d+/role$")) {
            return Role.ADMIN;
        }
        return null;
    }
}