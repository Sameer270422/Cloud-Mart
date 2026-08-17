package com.cloudmart.user.service;

import com.cloudmart.user.config.JwtUtil;
import com.cloudmart.user.dto.AuthResponse;
import com.cloudmart.user.dto.LoginRequest;
import com.cloudmart.user.dto.RegisterRequest;
import com.cloudmart.user.model.User;
import com.cloudmart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }
        // Simple bootstrap: the first account in a fresh system becomes the
        // admin, since there's otherwise no way to reach ADMIN-only
        // endpoints at all. Everyone after that is a regular customer.
        User.Role role = userRepository.count() == 0 ? User.Role.ADMIN : User.Role.CUSTOMER;
        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .build();
        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), Map.of(
                "userId", user.getId(),
                "role", user.getRole().name()
        ));
        return new AuthResponse(token, user.getId(), user.getFullName(), user.getEmail(), user.getRole().name());
    }
}
