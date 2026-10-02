package com.smartcampus.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.smartcampus.model.AuthSession;

public interface AuthSessionRepository extends MongoRepository<AuthSession,String> {
  Optional<AuthSession> findByToken(String token);
}
