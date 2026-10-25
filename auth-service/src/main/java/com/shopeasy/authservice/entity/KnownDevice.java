package com.shopeasy.authservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "known_devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_known_devices_user_device", columnNames = {"user_id", "device_hash"}))
public class KnownDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Hash of the device id. The raw id is never stored.
    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public KnownDevice() {
    }

    public KnownDevice(Long userId, String deviceHash, String label, String lastIp, Instant now) {
        this.userId = userId;
        this.deviceHash = deviceHash;
        this.label = label;
        this.lastIp = lastIp;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceHash() { return deviceHash; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }

    public Instant getFirstSeenAt() { return firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}