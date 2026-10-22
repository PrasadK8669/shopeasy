package com.shopeasy.productservice.service;

import org.springframework.stereotype.Service;

import com.shopeasy.productservice.dto.ContactRequest;
import com.shopeasy.productservice.dto.ContactResponse;
import com.shopeasy.productservice.entity.ContactMessage;
import com.shopeasy.productservice.repository.ContactMessageRepository;

@Service
public class ContactService {

    private final ContactMessageRepository repository;

    public ContactService(ContactMessageRepository repository) {
        this.repository = repository;
    }

    public ContactResponse save(ContactRequest request) {
        ContactMessage entity = new ContactMessage();
        entity.setName(request.name().trim());
        entity.setEmail(request.email().trim());
        entity.setTopic(request.topic());
        entity.setMessage(request.message().trim());
        return new ContactResponse(repository.save(entity).getId());
    }
}