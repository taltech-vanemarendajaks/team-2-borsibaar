package com.borsibaar.controller;

import com.borsibaar.entity.Role;
import com.borsibaar.entity.User;
import com.borsibaar.repository.OrganizationRepository;
import com.borsibaar.repository.RoleRepository;
import com.borsibaar.repository.UserRepository;
import com.borsibaar.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;

    public record MeResponse(String email, String name, String role, Long organizationId, boolean needsOnboarding) {
    }

    public record onboardingRequest(Long organizationId, boolean acceptTerms) {
    }

    @GetMapping
    public ResponseEntity<MeResponse> me() {
        try {
            // Allow users without organization (for onboarding check)
            User user = SecurityUtils.getCurrentUser(false);

            return ResponseEntity.ok(new MeResponse(
                    user.getEmail(),
                    user.getName(),
                    user.getRole() != null ? user.getRole().getName() : null,
                    user.getOrganizationId(),
                    user.getOrganizationId() == null));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            // Log unexpected errors for debugging
            // This will be handled by ApiExceptionHandler and return ProblemDetail
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve account information: " + e.getMessage(),
                    e);
        }
    }

    @PostMapping("/onboarding")
    @Transactional
    public ResponseEntity<Void> finish(@RequestBody onboardingRequest req) {
        try {
            if (req.organizationId() == null || !req.acceptTerms())
                return ResponseEntity.badRequest().build();

            if (!organizationRepository.existsById(req.organizationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization not found");
            }

            // Allow users without organization (that's the point of onboarding)
            User user = SecurityUtils.getCurrentUser(false);

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Required role ADMIN not found in database"));

            // Set org and role if needed (idempotent: do nothing if already set)
            if (user.getOrganizationId() == null) {
                // At least one user must be admin
                if (userRepository.findByOrganizationIdAndRole(req.organizationId(), adminRole).isEmpty()) {
                    user.setRole(adminRole);
                }
                user.setOrganizationId(req.organizationId());
                // Force DB constraint checks inside this try/catch (transaction commit would otherwise throw outside).
                userRepository.saveAndFlush(user);
            }

            // If later you add orgId to JWT, re-issue token here.
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            throw e; // Re-throw to be handled by exception handler
        } catch (Exception e) {
            // Log unexpected errors for debugging
            // This will be handled by ApiExceptionHandler and return ProblemDetail
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to complete onboarding: " + e.getMessage(),
                    e);
        }
    }

}
