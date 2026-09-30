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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the auth use case.
 *
 * <h2>What makes these "unit" tests</h2>
 * There is no {@code @SpringBootTest}, no database, no network, no mock
 * framework even. The three collaborators are hand-written fakes, because the
 * use case depends on the <em>ports</em> — and ports are, by construction,
 * trivial to implement in a test.
 *
 * <p>The whole class runs in milliseconds. In a layered design where
 * {@code AuthService} reached straight for a {@code UserRepository} backed by
 * JPA, this same test would need a full application context and a live
 * database, and would be slow and brittle enough that people stop writing
 * them. That is the argument for Onion Architecture in one example.
 *
 * <h2>What is deliberately not mocked</h2>
 * The assertions check real behaviour of {@code AuthService} — de-duplication,
 * hashing happening before persistence, role defaulting, the identical error
 * for unknown user and wrong password. Fakes record what happened; they do not
 * re-implement the logic under test.
 */
class AuthServiceTest {

    // ---------------------------------------------------------------- fakes

    /** In-memory {@link UserRepository}; no JPA, no SQL. */
    private static final class FakeUserRepository implements UserRepository {
        private final List<User> rows = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public Optional<User> findByUsername(String username) {
            return rows.stream().filter(u -> u.getUsername().equals(username)).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public User save(User user) {
            User stored = new User(ids.getAndIncrement(), user.getUsername(),
                    user.getPasswordHash(), user.getRole());
            rows.add(stored);
            return stored;
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(rows);
        }
    }

    /** Records calls so tests can assert the hash was taken <em>before</em> saving. */
    private static final class RecordingPasswordHasher implements PasswordHasher {
        private int hashCalls;
        private int matchCalls;

        @Override
        public String hash(String rawPassword) {
            hashCalls++;
            return "hashed::" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            matchCalls++;
            return ("hashed::" + rawPassword).equals(storedHash);
        }
    }

    /** Issues a deterministic fake token and reads it straight back. */
    private static final class FakeTokenService implements TokenService {
        @Override
        public String issue(User user) {
            return "fake-token." + user.getUsername() + "." + user.getRole().name();
        }

        @Override
        public Optional<TokenClaims> parse(String token) {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(parts[1], parts[2]));
        }

        @Override
        public long getExpiresInMillis() {
            return 86_400_000L;
        }
    }

    private FakeUserRepository repository;
    private RecordingPasswordHasher hasher;
    private AuthService authService;

    private AuthService newService() {
        return new AuthService(repository, hasher, new FakeTokenService());
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("stores a hashed password, never the plaintext")
        void hashesBeforePersisting() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            User saved = authService.register(new RegisterCommand("aditi", "secret123", Role.CUSTOMER));

            assertNotNull(saved.getId());
            assertEquals("aditi", saved.getUsername());
            assertEquals(Role.CUSTOMER, saved.getRole());
            assertEquals("hashed::secret123", saved.getPasswordHash());
            assertTrue(!saved.getPasswordHash().contains("secret123") || saved.getPasswordHash().length() > "secret123".length());
            assertEquals(1, hasher.hashCalls);
        }

        @Test
        @DisplayName("defaults to CUSTOMER when no role is supplied")
        void defaultsToCustomer() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            User saved = authService.register(new RegisterCommand("newbie", "secret123", null));

            assertEquals(Role.CUSTOMER, saved.getRole());
        }

        @Test
        @DisplayName("rejects a duplicate username without paying for a hash")
        void rejectsDuplicate() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            authService.register(new RegisterCommand("aditi", "secret123", Role.CUSTOMER));

            assertThrows(UsernameAlreadyExistsException.class,
                    () -> authService.register(new RegisterCommand("aditi", "other123", Role.CUSTOMER)));

            assertEquals(1, repository.findAll().size());
            // The uniqueness check runs first, so no BCrypt work was done for the
            // rejected request — which matters, because BCrypt is intentionally slow.
            assertEquals(1, hasher.hashCalls);
        }

        @Test
        @DisplayName("trims the username so 'aditi ' and 'aditi' collide")
        void normalisesUsername() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            authService.register(new RegisterCommand("  aditi  ", "secret123", Role.CUSTOMER));

            assertEquals("aditi", repository.findAll().get(0).getUsername());
            assertThrows(UsernameAlreadyExistsException.class,
                    () -> authService.register(new RegisterCommand("aditi", "secret123", Role.CUSTOMER)));
        }

        @Test
        @DisplayName("rejects a password shorter than 6 characters")
        void rejectsShortPassword() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            assertThrows(IllegalArgumentException.class,
                    () -> authService.register(new RegisterCommand("aditi", "12345", Role.CUSTOMER)));
        }

        @Test
        @DisplayName("rejects a password longer than BCrypt's 72-byte limit")
        void rejectsOverlongPassword() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            String tooLong = "a".repeat(73);
            assertThrows(IllegalArgumentException.class,
                    () -> authService.register(new RegisterCommand("aditi", tooLong, Role.CUSTOMER)));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a token carrying the username and role")
        void issuesTokenOnSuccess() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();
            authService.register(new RegisterCommand("boss", "secret123", Role.ADMIN));

            AuthenticatedUser result = authService.login(new LoginCommand("boss", "secret123"));

            assertEquals("boss", result.username());
            assertEquals(Role.ADMIN, result.role());
            assertNotNull(result.token());
            assertTrue(result.token().contains("ADMIN"));
            assertEquals(1, hasher.matchCalls);
        }

        @Test
        @DisplayName("throws for a wrong password")
        void rejectsWrongPassword() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();
            authService.register(new RegisterCommand("aditi", "secret123", Role.CUSTOMER));

            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginCommand("aditi", "wrong-password")));
        }

        @Test
        @DisplayName("throws the SAME exception for an unknown user — no username enumeration")
        void rejectsUnknownUserIdentically() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            authService = newService();

            InvalidCredentialsException unknownUser =
                    assertThrows(InvalidCredentialsException.class,
                            () -> authService.login(new LoginCommand("ghost", "whatever")));

            assertEquals("Invalid username or password", unknownUser.getMessage());
        }

        @Test
        @DisplayName("a token issued at login verifies straight back through the port")
        void tokenRoundTrips() {
            repository = new FakeUserRepository();
            hasher = new RecordingPasswordHasher();
            FakeTokenService tokens = new FakeTokenService();
            authService = new AuthService(repository, hasher, tokens);
            authService.register(new RegisterCommand("aditi", "secret123", Role.ADMIN));

            AuthenticatedUser result = authService.login(new LoginCommand("aditi", "secret123"));

            TokenService.TokenClaims claims = tokens.parse(result.token()).orElseThrow();
            assertEquals("aditi", claims.username());
            assertEquals("ADMIN", claims.role());
        }
    }
}
