package com.shopeasy.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.shopeasy.authservice.exception.EmailDeliveryException;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final String mode;
    private final String from;

    public MailService(JavaMailSender sender,
                       @Value("${app.mail.mode:smtp}") String mode,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.mode = mode.trim().toLowerCase();
        this.from = from.trim();

        switch (this.mode) {
            case "log" -> log.warn("app.mail.mode is 'log': emails are printed to this console and NOT sent. Development only.");
            case "smtp" -> {
                if (this.from.isEmpty()) {
                    throw new IllegalStateException(
                            "app.mail.from must be your Gmail address in application-local.yml (or use app.mail.mode: log while developing).");
                }
            }
            default -> throw new IllegalStateException("app.mail.mode must be 'smtp' or 'log'.");
        }
    }

    // Sign-up code. Throws if the email cannot be sent, so the sign-up can be rolled back.
    public void sendVerificationCode(String to, String name, int expiryMinutes, String code) {
        String body = "Hi " + name + ",\n\n"
                + "Your ShopEasy verification code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minutes. If you did not create an account, you can ignore this email.\n\n"
                + "Never share this code with anyone. ShopEasy will never ask for it.";
        send(to, "Your ShopEasy verification code", body);
    }

    // Runs in the background, so the response time does not reveal whether an account exists
    @Async("mailExecutor")
    public void sendResetCode(String to, String name, int expiryMinutes, String code) {
        String body = "Hi " + name + ",\n\n"
                + "Your ShopEasy password reset code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minutes. If you did not ask to reset your password, you can ignore this email. Your password has not been changed.\n\n"
                + "Never share this code with anyone. ShopEasy will never ask for it.";
        sendQuietly(to, "Your ShopEasy password reset code", body);
    }

    @Async("mailExecutor")
    public void sendPasswordChangedNotice(String to, String name) {
        String body = "Hi " + name + ",\n\n"
                + "The password for your ShopEasy account was just changed.\n\n"
                + "If this was you, no action is needed. If it was not you, reset your password again right away and contact us.";
        sendQuietly(to, "Your ShopEasy password was changed", body);
    }

    private void sendQuietly(String to, String subject, String body) {
        try {
            send(to, subject, body);
        } catch (RuntimeException e) {
            log.error("Background email '{}' to {} failed.", subject, to);
        }
    }

    private void send(String to, String subject, String body) {
        if ("log".equals(mode)) {
            log.info("[DEV MAIL] to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Could not send email to {}: {} ({})", to, e.getMessage(), e.getMostSpecificCause().getMessage());
            throw new EmailDeliveryException();
        }
    }
}