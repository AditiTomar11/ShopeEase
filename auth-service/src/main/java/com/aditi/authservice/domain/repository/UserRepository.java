package com.aditi.authservice.domain.repository;

import com.aditi.authservice.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findByUsername(String username);
}