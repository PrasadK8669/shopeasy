package com.shopeasy.authservice.service;

import com.shopeasy.authservice.dto.AuthResponse;
import com.shopeasy.authservice.dto.LoginRequest;
import com.shopeasy.authservice.dto.RegisterRequest;
import com.shopeasy.authservice.dto.RegisterResponse;
import com.shopeasy.authservice.dto.ResendOtpRequest;
import com.shopeasy.authservice.dto.UserResponse;
import com.shopeasy.authservice.dto.VerifyEmailRequest;
import com.shopeasy.authservice.entity.Role;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.EmailAlreadyUsedException;
import com.shopeasy.authservice.exception.EmailNotVerifiedException;
import com.shopeasy.authservice.exception.InvalidCredentialsException;
import com.shopeasy.authservice.exception.InvalidOtpException;
import com.shopeasy.authservice.repository.UserRepository;
import com.shopeasy.authservice.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        // Used so a login for an unknown email takes as long as one for a real email
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (tooLong(request.password())) {
            throw new IllegalArgumentException("Password must be 72 bytes or fewer");
        }
        String email = normalise(request.email());

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isEmailVerified()) {
                throw new EmailAlreadyUsedException();
            }
            // A sign-up that was never finished: send a fresh code, never touch the stored data
            otpService.issue(user);
            return new RegisterResponse(email, true);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER); // everyone who registers is a USER, never an admin
        user.setEmailVerified(false);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(); // two sign-ups at once with the same email
        }

        otpService.issue(user); // if the email fails, the sign-up is rolled back too
        return new RegisterResponse(email, true);
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
        User user = found.get();
        // Only reached with the correct password, so this reveals nothing to a guesser
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return toResponse(user);
    }

    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(normalise(request.email()))
                .orElseThrow(InvalidOtpException::new);
        if (user.isEmailVerified()) {
            throw new InvalidOtpException();
        }
        otpService.verify(user, request.code());
        return toResponse(user);
    }

    // Always behaves the same for the caller, so it cannot be used to discover which emails exist
    @Transactional
    public void resendCode(ResendOtpRequest request) {
        userRepository.findByEmail(normalise(request.email()))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(otpService::issue);
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