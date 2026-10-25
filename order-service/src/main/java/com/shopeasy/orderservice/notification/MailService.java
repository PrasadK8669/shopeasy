package com.shopeasy.orderservice.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    // Throws if the email cannot be sent. The caller decides what to do about it.
    public void send(String to, String subject, String body) {
        if ("log".equals(mode)) {
            log.info("[DEV MAIL] to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}