package com.shopeasy.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopeasy.authservice.entity.EmailOtp;
import com.shopeasy.authservice.entity.OtpPurpose;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findByUserIdAndPurpose(Long userId, OtpPurpose purpose);
}