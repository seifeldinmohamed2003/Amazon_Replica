package com.team27.amazon.billing.security.filter;

import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.billing.security.handler.*;
import com.team27.amazon.billing.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TransactionRepository transactionRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TransactionRepository transactionRepository) {
        this.jwtService = jwtService;
        this.transactionRepository = transactionRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/transactions/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Build the AuthHandler chain (Chain of Responsibility)
        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
            .setNext(new UserLoaderHandler(transactionRepository));

        AuthContext ctx = new AuthContext(request);
        String error = head.handle(ctx);

        if (error != null) {
            int status = error.startsWith("403") ? 403 : 401;
            response.setStatus(status);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + error.substring(4) + "\"}");
            return;
        }

        // Populate Spring Security context
        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}