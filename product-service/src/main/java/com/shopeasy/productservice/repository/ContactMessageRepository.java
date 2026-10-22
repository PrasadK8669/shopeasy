package com.shopeasy.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopeasy.productservice.entity.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
}