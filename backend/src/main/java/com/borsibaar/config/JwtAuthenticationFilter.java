package com.borsibaar.config;

import com.borsibaar.entity.User;
import com.borsibaar.repository.UserRepository;
import com.borsibaar.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * JWT Authentication Filter that intercepts every request and validates JWT
 * tokens from cookies.
 * If valid, sets the authentication in the SecurityContext for downstream
 * authorization.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip JWT authentication for OAuth2 and auth login endpoints
        // But allow JWT authentication for /auth/logout
        String requestPath = request.getRequestURI();
        if (requestPath.startsWith("/oauth2/") ||
                requestPath.startsWith("/login/oauth2/") ||
                (requestPath.startsWith("/auth/") && !requestPath.equals("/auth/logout"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token from cookie
        String token = extractJwtFromCookie(request);

        // If no token, continue without authentication (Spring Security will handle
        // authorization)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Parse and validate JWT token
            Claims claims = jwtService.parseToken(token);
            String email = claims.getSubject();

            // Load user from database and set JWT authentication
            // This replaces any existing OAuth2 session authentication
            if (email != null) {
                // Use findByEmailWithRole to eagerly fetch role and avoid
                // LazyInitializationException
                Optional<User> userOptional = userRepository.findByEmailWithRole(email);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    // Create authentication token with user details and role
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user, // Principal - the authenticated user
                            null, // Credentials - not needed after authentication
                            user.getRole() != null
                                    ? Collections.singletonList(
                                            new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()))
                                    : Collections.emptyList());

                    // Set additional details (IP address, session ID, etc.)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in SecurityContext (replaces OAuth2 authentication if
                    // present)
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("JWT authentication set in SecurityContext for user: " + email);
                } else {
                    logger.warn("User not found in database for email: " + email);
                }
            }
        } catch (Exception e) {
            // If token is invalid, log and continue without authentication
            // Spring Security will handle the 401/403 response
            logger.warn("JWT token validation failed: " + e.getMessage(), e);
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from the "jwt" cookie.
     *
     * @param request HTTP request
     * @return JWT token or null if not found
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
