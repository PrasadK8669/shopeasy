package com.shopeasy.authservice.repository;

import com.shopeasy.authservice.entity.EmailOtp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findByUserId(Long userId);
}