package com.borsibaar.controller;

import com.borsibaar.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    @Value("${app.frontend.url}")
    private String frontendUrl;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login/success")
    public void success(HttpServletRequest request, HttpServletResponse response, OAuth2AuthenticationToken auth) throws IOException {
        var result = authService.processOAuthLogin(auth);

        Cookie cookie = new Cookie("jwt", result.dto().token());
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS enabled with domain
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 1 day
        response.addCookie(cookie);

        String redirectPath = result.needsOnboarding() ? "/onboarding" : "/dashboard";

        String requestHost = request.getHeader("X-Forwarded-Host");
        if (requestHost == null || requestHost.isBlank()) {
            requestHost = request.getHeader("Host");
        }
        if (requestHost == null) {
            requestHost = "";
        }

        // Prefer configured frontendUrl (useful for local dev where frontend is on a different port).
        // But if it's clearly misconfigured (e.g. localhost in production), fall back to a relative redirect.
        boolean frontendUrlLooksWrongForRequest =
                frontendUrl != null
                        && !frontendUrl.isBlank()
                        && frontendUrl.contains("localhost")
                        && !requestHost.contains("localhost")
                        && !requestHost.isBlank();

        if (frontendUrl == null || frontendUrl.isBlank() || frontendUrlLooksWrongForRequest) {
            response.sendRedirect(redirectPath);
            return;
        }

        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        response.sendRedirect(base + redirectPath);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Invalidate the server-side session (removes OAuth2 authentication)
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Clear the Spring Security context
        SecurityContextHolder.clearContext();

        // Clear the JWT cookie
        Cookie jwtCookie = new Cookie("jwt", "");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true); // HTTPS enabled with domain
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0); // Expire immediately
        response.addCookie(jwtCookie);

        return ResponseEntity.ok().body(new LogoutResponse("Logged out successfully"));
    }

    private record LogoutResponse(String message) {
    }
}
