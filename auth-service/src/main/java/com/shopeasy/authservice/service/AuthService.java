package com.shopeasy.authservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.authservice.dto.AuthResponse;
import com.shopeasy.authservice.dto.LoginRequest;
import com.shopeasy.authservice.dto.RegisterRequest;
import com.shopeasy.authservice.dto.UserResponse;
import com.shopeasy.authservice.entity.Role;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.EmailAlreadyUsedException;
import com.shopeasy.authservice.exception.InvalidCredentialsException;
import com.shopeasy.authservice.repository.UserRepository;
import com.shopeasy.authservice.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Used so a login for an unknown email takes as long as one for a real email
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (tooLong(request.password())) {
            throw new IllegalArgumentException("Password must be 72 bytes or fewer");
        }
        String email = normalise(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER); // everyone who registers is a USER, never an admin

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(); // two sign-ups at once with the same email
        }
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        if (tooLong(request.password())) {
            throw new InvalidCredentialsException();
        }
        Optional<User> found = userRepository.findByEmail(normalise(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);

        // One generic message for "no such email" and "wrong password"
        if (found.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return toResponse(found.get());
    }

    private AuthResponse toResponse(User user) {
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole().name());
        return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.expirySeconds(), userResponse);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72; // BCrypt limit
    }
}