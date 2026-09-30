package com.aditi.authservice.domain.repository;

import com.aditi.authservice.domain.model.User;

import java.util.List;
import java.util.Optional;

/**
 * The domain's view of user storage. Declared as an interface in the innermost
 * ring and implemented in infrastructure (see
 * {@code infrastructure.persistence.UserRepositoryImpl}).
 *
 * <p>Signatures speak in {@link User} domain objects, never in
 * {@code UserEntity}, so JPA can be swapped for MongoDB, jOOQ or plain JDBC
 * without the domain noticing.
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    User save(User user);

    List<User> findAll();
}
