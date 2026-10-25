package com.shopeasy.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.authservice.entity.KnownDevice;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.repository.KnownDeviceRepository;
import com.shopeasy.authservice.security.ClientInfo;

@Service
public class DeviceService {

    private static final int MAX_DEVICES_PER_USER = 20;
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    private final KnownDeviceRepository devices;
    private final MailService mailService;

    public DeviceService(KnownDeviceRepository devices, MailService mailService) {
        this.devices = devices;
        this.mailService = mailService;
    }

    // Called after a successful sign-in. Sends an alert if this device is new,
    // except for an account's very first device (otherwise every new user would get one).
    @Transactional
    public void recordSignIn(User user, ClientInfo client) {
        Instant now = Instant.now();
        String hash = fingerprint(client);
        String label = describe(client.userAgent());
        String ip = client.ip() == null ? null : client.ip().substring(0, Math.min(45, client.ip().length()));

        Optional<KnownDevice> known = devices.findByUserIdAndDeviceHash(user.getId(), hash);
        if (known.isPresent()) {
            KnownDevice device = known.get();
            device.setLastSeenAt(now);
            device.setLastIp(ip);
            device.setLabel(label);
            devices.save(device);
            return;
        }

        boolean hadDevices = devices.countByUserId(user.getId()) > 0;
        devices.save(new KnownDevice(user.getId(), hash, label, ip, now));

        // Keep the table small: forget the device that was used longest ago
        if (devices.countByUserId(user.getId()) > MAX_DEVICES_PER_USER) {
            devices.findFirstByUserIdOrderByLastSeenAtAsc(user.getId()).ifPresent(devices::delete);
        }

        if (hadDevices) {
            mailService.sendNewSignInAlert(user.getEmail(), user.getName(), label, ip, now);
        }
    }

    // Uses the random id the browser stores. Without one (for example a script), falls back to the User-Agent.
    private static String fingerprint(ClientInfo client) {
        String id = client.deviceId();
        String source = (id != null && DEVICE_ID.matcher(id).matches())
                ? "id:" + id
                : "ua:" + client.userAgent();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // A readable label built only from fixed words, so nothing from the request ends up in the email
    static String describe(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") ? "Opera"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari"
                : ua.contains("PowerShell") ? "PowerShell"
                : "an unknown browser";
        String os = ua.contains("Windows") ? "Windows"
                : ua.contains("Android") ? "Android"
                : (ua.contains("iPhone") || ua.contains("iPad")) ? "iOS"
                : ua.contains("Mac OS X") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : "an unknown system";
        return browser + " on " + os;
    }
}