package com.shopeasy.authservice.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.authservice.entity.EmailOtp;
import com.shopeasy.authservice.entity.OtpPurpose;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.InvalidOtpException;
import com.shopeasy.authservice.repository.EmailOtpRepository;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    public OtpService(EmailOtpRepository otpRepository, PasswordEncoder passwordEncoder,
                      MailService mailService) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    // Sign-in codes are short-lived. Sign-up and reset codes live a little longer.
    private static long expiryMinutes(OtpPurpose purpose) {
        return purpose == OtpPurpose.LOGIN_2FA ? 5 : 10;
    }

    // Creates and emails a fresh code. Returns false (and sends nothing) if asked too soon or too often.
    @Transactional
    public boolean issue(User user, OtpPurpose purpose) {
        Instant now = Instant.now();
        long expiry = expiryMinutes(purpose);
        EmailOtp otp = otpRepository.findByUserIdAndPurpose(user.getId(), purpose)
                .orElseGet(() -> new EmailOtp(user.getId(), purpose));

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
        otp.setExpiresAt(now.plus(expiry, ChronoUnit.MINUTES));
        otp.setAttempts(0);
        otp.setLastSentAt(now);
        otp.setSentCount(otp.getSentCount() + 1);
        otpRepository.save(otp);

        switch (purpose) {
            case PASSWORD_RESET -> mailService.sendResetCode(user.getEmail(), user.getName(), (int) expiry, code); // background
            // These two throw if the email cannot be sent, which rolls the whole transaction back
            case LOGIN_2FA -> mailService.sendLoginCode(user.getEmail(), user.getName(), (int) expiry, code);
            case EMAIL_VERIFICATION -> mailService.sendVerificationCode(user.getEmail(), user.getName(), (int) expiry, code);
        }
        return true;
    }

    // Checks the code and, if it is right, deletes it so it can only be used once.
    // noRollbackFor: a wrong guess must still be saved, or the attempt counter would never grow.
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void consume(User user, OtpPurpose purpose, String code) {
        Instant now = Instant.now();
        EmailOtp otp = otpRepository.findByUserIdAndPurpose(user.getId(), purpose)
                .orElseThrow(InvalidOtpException::new);

        if (now.isAfter(otp.getExpiresAt()) || otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidOtpException(); // expired or out of tries: the user must request a new code
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new InvalidOtpException();
        }
        otpRepository.delete(otp);
    }
}