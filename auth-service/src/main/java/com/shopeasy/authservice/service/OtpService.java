package com.shopeasy.authservice.service;

import com.shopeasy.authservice.entity.EmailOtp;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.InvalidOtpException;
import com.shopeasy.authservice.repository.EmailOtpRepository;
import com.shopeasy.authservice.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    public OtpService(EmailOtpRepository otpRepository, UserRepository userRepository,
                      PasswordEncoder passwordEncoder, MailService mailService) {
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    // Creates and emails a fresh code. Returns false (and sends nothing) if asked too soon or too often.
    @Transactional
    public boolean issue(User user) {
        Instant now = Instant.now();
        EmailOtp otp = otpRepository.findByUserId(user.getId()).orElseGet(() -> new EmailOtp(user.getId()));

        if (otp.getLastSentAt() != null && now.isBefore(otp.getLastSentAt().plusSeconds(COOLDOWN_SECONDS))) {
            return false;
        }
        if (otp.getWindowStart() == null || !now.isBefore(otp.getWindowStart().plus(1, ChronoUnit.HOURS))) {
            otp.setWindowStart(now);
            otp.setSentCount(0);
        }
        if (otp.getSentCount() >= MAX_SENDS_PER_HOUR) {
            return false;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.setCodeHash(passwordEncoder.encode(code)); // only the hash is stored
        otp.setExpiresAt(now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES));
        otp.setAttempts(0);
        otp.setLastSentAt(now);
        otp.setSentCount(otp.getSentCount() + 1);
        otpRepository.save(otp);

        // If the email cannot be sent, this throws and the whole transaction rolls back
        mailService.sendOtp(user.getEmail(), user.getName(), (int) EXPIRY_MINUTES, code);
        return true;
    }

    // noRollbackFor: a wrong guess must still be saved, or the attempt counter would never grow
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void verify(User user, String code) {
        Instant now = Instant.now();
        EmailOtp otp = otpRepository.findByUserId(user.getId()).orElseThrow(InvalidOtpException::new);

        if (now.isAfter(otp.getExpiresAt()) || otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidOtpException(); // expired or out of tries: the user must request a new code
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new InvalidOtpException();
        }

        otpRepository.delete(otp);
        user.setEmailVerified(true);
        userRepository.save(user);
    }
}