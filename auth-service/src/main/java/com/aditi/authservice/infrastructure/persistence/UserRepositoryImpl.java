package com.aditi.authservice.infrastructure.persistence;

import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapts Spring Data JPA to the domain's {@link UserRepository} port.
 *
 * <p>This class is the seam that makes the onion testable. The domain declares
 * "I need to load a user by username"; this class answers "…and here is how,
 * with SQL". Because the port is declared inwards, swapping this adapter for a
 * Mongo one changes nothing above it.
 *
 * <p>The class name matters for the same reason: by convention Spring Data
 * would pick up any interface ending in "Repository", so the implementation is
 * suffixed {@code Impl} and left off the repository interface to keep the
 * DataJpa proxy generation unambiguous.
 *
 * <p>It carries no stereotype annotation at all: it is instantiated explicitly
 * by {@code BeanModule}, the composition root. See that class for why that is
 * worth the extra four lines.
 */
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryImpl(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(UserEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    @Transactional
    public User save(User user) {
        return jpaRepository.save(UserEntity.from(user)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return jpaRepository.findAll().stream()
                .map(UserEntity::toDomain)
                .toList();
    }
}
