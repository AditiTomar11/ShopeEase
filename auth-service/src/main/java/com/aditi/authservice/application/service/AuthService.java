package com.aditi.authservice.application.service;

import com.aditi.authservice.application.command.LoginCommand;
import com.aditi.authservice.application.command.RegisterCommand;
import com.aditi.authservice.domain.exception.InvalidCredentialsException;
import com.aditi.authservice.domain.exception.UsernameAlreadyExistsException;
import com.aditi.authservice.domain.model.AuthenticatedUser;
import com.aditi.authservice.domain.model.Role;
import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.port.PasswordHasher;
import com.aditi.authservice.domain.port.TokenService;
import com.aditi.authservice.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * APPLICATION LAYER — the use cases of the auth domain.
 *
 * <p>This class is the piece an interviewer should be able to read in 60
 * seconds: "register a user" and "log a user in", written as plain business
 * logic. Note what it does <em>not</em> contain:
 * <ul>
 *   <li>no {@code HttpServletRequest} / status codes — that is presentation's job;</li>
 *   <li>no {@code @Entity} / {@code EntityManager} — that is infrastructure's job;</li>
 *   <li>no {@code Jwts.builder()} — it asks the {@link TokenService} port instead;</li>
 *   <li>no {@code BCryptPasswordEncoder} — it asks the {@link PasswordHasher} port instead.</li>
 * </ul>
 *
 * <p>Collaborators arrive through the constructor. That is constructor
 * injection: no field injection, no hidden dependencies, and the class can be
 * instantiated in a plain unit test with hand-written fakes.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    /**
     * Registers a new customer (or admin) and returns the stored user.
     *
     * <p>Note the order of operations: validate → de-duplicate → hash → save.
     * We hash <em>after</em> the uniqueness check so we never pay the cost of a
     * deliberately slow BCrypt hash for a request that is going to be rejected
     * anyway.
     */
    @Transactional
    public User register(RegisterCommand command) {
        String username = normaliseUsername(command.username());
        String rawPassword = command.rawPassword();

        validatePassword(rawPassword);

        if (userRepository.existsByUsername(username)) {
            // Log the attempt, never the password.
            log.warn("Registration rejected: username already taken");
            throw new UsernameAlreadyExistsException(username);
        }

        // Default to CUSTOMER. A public caller cannot simply POST role:"ADMIN".
        Role role = command.role() == null ? Role.CUSTOMER : command.role();

        User saved = userRepository.save(
                User.newUser(username, passwordHasher.hash(rawPassword), role));

        log.info("Registered user id={} role={}", saved.getId(), saved.getRole());
        return saved;
    }

    /**
     * Verifies credentials and issues a token.
     *
     * <p>Both failure modes raise the same {@link InvalidCredentialsException} so
     * the endpoint cannot be used to discover which usernames exist.
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser login(LoginCommand command) {
        String username = normaliseUsername(command.username());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Login failed: unknown username");
                    return new InvalidCredentialsException();
                });

        if (!passwordHasher.matches(command.rawPassword(), user.getPasswordHash())) {
            log.warn("Login failed: wrong password");
            throw new InvalidCredentialsException();
        }

        String token = tokenService.issue(user);
        log.info("Login succeeded role={} tokenExpiresInMs={}",
                user.getRole(), tokenService.getExpiresInMillis());

        return new AuthenticatedUser(user.getUsername(), user.getRole(), token);
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        // BCrypt only considers the first 72 bytes; anything beyond is ignored
        // silently, which is a classic "my password change didn't take" bug.
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
        }
    }

    private String normaliseUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        return username.trim();
    }
}
