package com.team27.amazon.user.repository;

import com.team27.amazon.common.events.AuthEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {
}