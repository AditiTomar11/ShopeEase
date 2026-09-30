package com.aditi.authservice.presentation.controller;

import com.aditi.authservice.application.command.LoginCommand;
import com.aditi.authservice.application.command.RegisterCommand;
import com.aditi.authservice.application.service.AuthService;
import com.aditi.authservice.domain.model.AuthenticatedUser;
import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.port.TokenService;
import com.aditi.authservice.presentation.dto.AuthResponse;
import com.aditi.authservice.presentation.dto.LoginRequest;
import com.aditi.authservice.presentation.dto.MessageResponse;
import com.aditi.authservice.presentation.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PRESENTATION LAYER — HTTP in, HTTP out.
 *
 * <p>Its entire job is translation:
 * <ul>
 *   <li>JSON → command object → use case, and</li>
 *   <li>domain result → DTO + status code, and</li>
 *   <li>it is the <em>only</em> layer allowed to know that HTTP exists.</li>
 * </ul>
 *
 * <p>Look at how little logic is here: no {@code if (user == null)}, no hashing,
 * no token building. All of that lives behind {@link AuthService}. That thinness
 * is the practical test of whether Onion Architecture is real or just folder
 * decoration.
 *
 * <p>The controller is stateless and thread-safe — every request is served from
 * local variables plus a shared, immutable service bean — which is what lets
 * Tomcat's thread pool handle it without locking.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    /**
     * Registers a user. Returns 201 Created rather than 200 because a resource
     * now exists — the small detail clients and tests frequently rely on.
     */
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        User saved = authService.register(new RegisterCommand(
                request.username(),
                request.password(),
                request.toRoleOrDefault()));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MessageResponse.of("User registered successfully"));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = authService.login(
                new LoginCommand(request.username(), request.password()));

        return AuthResponse.from(user, tokenService.getExpiresInMillis());
    }

    /**
     * A genuinely protected endpoint, used to prove the JWT filter works.
     *
     * <p>There is no path parameter and no database lookup: the
     * {@link Authentication} was already populated from the token by
     * {@code JwtAuthenticationFilter}, so "who am I" is answered by the
     * credential itself.
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "role", authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                        .findFirst()
                        .orElse("CUSTOMER"));
    }

    /** Cheap liveness probe for Render — deliberately touches no database. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "auth-service");
    }
}
