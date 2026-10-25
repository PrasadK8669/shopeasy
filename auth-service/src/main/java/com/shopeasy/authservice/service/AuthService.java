package com.shopeasy.authservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.authservice.dto.AuthResponse;
import com.shopeasy.authservice.dto.ForgotPasswordRequest;
import com.shopeasy.authservice.dto.LoginRequest;
import com.shopeasy.authservice.dto.LoginResendRequest;
import com.shopeasy.authservice.dto.LoginResponse;
import com.shopeasy.authservice.dto.LoginVerifyRequest;
import com.shopeasy.authservice.dto.RegisterRequest;
import com.shopeasy.authservice.dto.RegisterResponse;
import com.shopeasy.authservice.dto.ResendOtpRequest;
import com.shopeasy.authservice.dto.ResetPasswordRequest;
import com.shopeasy.authservice.dto.UserResponse;
import com.shopeasy.authservice.dto.VerifyEmailRequest;
import com.shopeasy.authservice.entity.OtpPurpose;
import com.shopeasy.authservice.entity.Role;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.EmailAlreadyUsedException;
import com.shopeasy.authservice.exception.EmailNotVerifiedException;
import com.shopeasy.authservice.exception.InvalidChallengeException;
import com.shopeasy.authservice.exception.InvalidCredentialsException;
import com.shopeasy.authservice.exception.InvalidOtpException;
import com.shopeasy.authservice.repository.UserRepository;
import com.shopeasy.authservice.security.ClientInfo;
import com.shopeasy.authservice.security.JwtService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final MailService mailService;
    private final DeviceService deviceService;
    private final boolean adminMfaEnabled;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, OtpService otpService, MailService mailService,
                       DeviceService deviceService,
                       @Value("${app.mfa.admin-enabled:true}") boolean adminMfaEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.mailService = mailService;
        this.deviceService = deviceService;
        this.adminMfaEnabled = adminMfaEnabled;
        // Used so a login for an unknown email takes as long as one for a real email
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");

        if (!adminMfaEnabled) {
            log.warn("app.mfa.admin-enabled is false: admins can sign in with just a password. Emergency use only.");
        }
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
            otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION);
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

        otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION); // if the email fails, the sign-up is rolled back too
        return new RegisterResponse(email, true);
    }

    // Step 1 for admins (password), then a code is emailed. Everyone else is signed in right here.
    public LoginResponse login(LoginRequest request, ClientInfo client) {
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

        if (user.getRole() == Role.ADMIN && adminMfaEnabled) {
            // May send nothing if a code went out a moment ago. The earlier code still works.
            otpService.issue(user, OtpPurpose.LOGIN_2FA);
            return LoginResponse.challenge(jwtService.generateChallenge(user), mask(user.getEmail()));
        }
        return LoginResponse.signedIn(completeSignIn(user, client));
    }

    // Step 2 for admins: the challenge proves the password step passed, the code proves the inbox
    public AuthResponse verifyLogin(LoginVerifyRequest request, ClientInfo client) {
        User user = adminFromChallenge(request.challengeToken());
        otpService.consume(user, OtpPurpose.LOGIN_2FA, request.code());
        return completeSignIn(user, client);
    }

    public void resendLoginCode(LoginResendRequest request) {
        User user = adminFromChallenge(request.challengeToken());
        otpService.issue(user, OtpPurpose.LOGIN_2FA);
    }

    // Not transactional on purpose: the code is consumed in its own transaction,
    // and recording the device must never be able to undo it.
    public AuthResponse verifyEmail(VerifyEmailRequest request, ClientInfo client) {
        User user = userRepository.findByEmail(normalise(request.email()))
                .orElseThrow(InvalidOtpException::new);
        if (user.isEmailVerified()) {
            throw new InvalidOtpException();
        }
        otpService.consume(user, OtpPurpose.EMAIL_VERIFICATION, request.code());
        user.setEmailVerified(true);
        userRepository.save(user);
        return completeSignIn(user, client);
    }

    // Always behaves the same for the caller, so it cannot be used to discover which emails exist
    @Transactional
    public void resendCode(ResendOtpRequest request) {
        userRepository.findByEmail(normalise(request.email()))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION));
    }

    // Same behaviour for every email. Unknown ones do the same amount of hashing work as real ones.
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(normalise(request.email()))
                .filter(User::isEmailVerified)
                .ifPresentOrElse(
                        user -> otpService.issue(user, OtpPurpose.PASSWORD_RESET),
                        () -> passwordEncoder.encode("not-a-real-code-1"));
    }

    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void resetPassword(ResetPasswordRequest request) {
        if (tooLong(request.newPassword())) {
            throw new IllegalArgumentException("Password must be 72 bytes or fewer");
        }
        User user = userRepository.findByEmail(normalise(request.email()))
                .filter(User::isEmailVerified)
                .orElseThrow(InvalidOtpException::new);

        otpService.consume(user, OtpPurpose.PASSWORD_RESET, request.code());

        // Checked only after the code is proven correct, so it cannot be used to guess the current password.
        // This exception rolls the transaction back, so the code stays valid for another try.
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Choose a password you have not used before");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        mailService.sendPasswordChangedNotice(user.getEmail(), user.getName());
    }

    private User adminFromChallenge(String challengeToken) {
        Long userId = jwtService.parseChallenge(challengeToken);
        return userRepository.findById(userId)
                .filter(u -> u.getRole() == Role.ADMIN && u.isEmailVerified())
                .orElseThrow(InvalidChallengeException::new);
    }

    // Records the device (and may send a new-device alert), then issues the real login token.
    // A problem with device tracking must never stop someone from signing in.
    private AuthResponse completeSignIn(User user, ClientInfo client) {
        try {
            deviceService.recordSignIn(user, client);
        } catch (RuntimeException e) {
            log.warn("Could not record the sign-in device for user {}: {}", user.getId(), e.getMessage());
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole().name());
        return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.expirySeconds(), userResponse);
    }

    // p***9@gmail.com
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.charAt(at - 1) + email.substring(at);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72; // BCrypt limit
    }
}