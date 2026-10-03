package com.aditi.authservice.application.service;

import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.repository.UserRepository;
import com.aditi.authservice.dto.AuthRequest;
import com.aditi.authservice.dto.AuthResponse;
import com.aditi.authservice.infrastructure.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String register(AuthRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }
        User user = new User(
                null,
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getRole() != null ? request.getRole() : "CUSTOMER"
        );
        userRepository.save(user);
        return "User registered successfully";
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }
}