package com.shopeasy.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopeasy.authservice.entity.KnownDevice;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, Long> {

    Optional<KnownDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash);

    long countByUserId(Long userId);

    Optional<KnownDevice> findFirstByUserIdOrderByLastSeenAtAsc(Long userId);
}