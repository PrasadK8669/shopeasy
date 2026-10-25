package com.shopeasy.authservice.service;

import com.shopeasy.authservice.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

    public void sendOtp(String to, String name, int expiryMinutes, String code) {
        String body = "Hi " + name + ",\n\n"
                + "Your ShopEasy verification code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minutes. If you did not create an account, you can ignore this email.\n\n"
                + "Never share this code with anyone. ShopEasy will never ask for it.";
        send(to, "Your ShopEasy verification code", body);
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
            log.error("Could not send email to {}: {}", to, e.getMessage());
            throw new EmailDeliveryException();
        }
    }
}