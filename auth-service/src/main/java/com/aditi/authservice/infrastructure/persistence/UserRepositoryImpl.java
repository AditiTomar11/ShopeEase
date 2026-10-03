package com.aditi.authservice.infrastructure.persistence;

import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.repository.UserRepository;
import com.aditi.authservice.infrastructure.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Autowired
    public UserRepositoryImpl(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = new UserEntity(user.getId(), user.getUsername(), user.getPassword(), user.getRole());
        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    private User toDomain(UserEntity e) {
        return new User(e.getId(), e.getUsername(), e.getPassword(), e.getRole());
    }
}