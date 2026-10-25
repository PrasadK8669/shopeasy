package com.shopeasy.authservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeasy.authservice.dto.AuthResponse;
import com.shopeasy.authservice.dto.ForgotPasswordRequest;
import com.shopeasy.authservice.dto.LoginRequest;
import com.shopeasy.authservice.dto.MessageResponse;
import com.shopeasy.authservice.dto.RegisterRequest;
import com.shopeasy.authservice.dto.RegisterResponse;
import com.shopeasy.authservice.dto.ResendOtpRequest;
import com.shopeasy.authservice.dto.ResetPasswordRequest;
import com.shopeasy.authservice.dto.VerifyEmailRequest;
import com.shopeasy.authservice.exception.InvalidCredentialsException;
import com.shopeasy.authservice.exception.TooManyRequestsException;
import com.shopeasy.authservice.security.LoginThrottle;
import com.shopeasy.authservice.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginThrottle loginThrottle;

    public AuthController(AuthService authService, LoginThrottle loginThrottle) {
        this.authService = authService;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-otp")
    public MessageResponse resend(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendCode(request);
        return new MessageResponse("If a sign-up is waiting for verification for this email, a new code has been sent.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return new MessageResponse("If an account exists for this email, a reset code has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Your password has been updated. You can sign in now.");
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // The same lock applies whether or not the email exists, so lockouts reveal nothing
        String key = http.getRemoteAddr() + "|" + request.email().trim().toLowerCase();

        long wait = loginThrottle.retryAfterSeconds(key);
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }
        try {
            AuthResponse response = authService.login(request);
            loginThrottle.recordSuccess(key);
            return response;
        } catch (InvalidCredentialsException e) {
            loginThrottle.recordFailure(key);
            throw e;
        }
    }
}