package com.team27.amazon.shipping.security.filter;

import java.io.IOException;
import java.util.Collections;

import com.team27.amazon.shipping.security.JwtService;
import com.team27.amazon.shipping.security.auth.AuthContext;
import com.team27.amazon.shipping.security.auth.AuthHandler;
import com.team27.amazon.shipping.security.auth.AuthResult;
import com.team27.amazon.shipping.security.auth.RoleAuthorizationHandler;
import com.team27.amazon.shipping.security.auth.SignatureValidationHandler;
import com.team27.amazon.shipping.security.auth.TokenExtractionHandler;
import com.team27.amazon.shipping.security.auth.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        return path.startsWith("/actuator")
                || "/api/shipments/health".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/error".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthContext context = new AuthContext(request);
        AuthHandler head = buildHandlerChain();
        AuthResult result = head.handle(context);

        if (!result.isSuccess()) {
            response.sendError(result.getStatus(), result.getMessage());
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                context.getUserId(),
                context.getToken(),
                context.getRole() == null
                        ? Collections.emptyList()
                        : Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + context.getRole()))
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private AuthHandler buildHandlerChain() {
        AuthHandler tokenExtractionHandler = new TokenExtractionHandler();
        AuthHandler signatureValidationHandler = new SignatureValidationHandler(jwtService);
        AuthHandler userLoaderHandler = new UserLoaderHandler(jwtService, jdbcTemplate);
        AuthHandler roleAuthorizationHandler = new RoleAuthorizationHandler();

        tokenExtractionHandler.setNext(signatureValidationHandler);
        signatureValidationHandler.setNext(userLoaderHandler);
        userLoaderHandler.setNext(roleAuthorizationHandler);

        return tokenExtractionHandler;
    }
}