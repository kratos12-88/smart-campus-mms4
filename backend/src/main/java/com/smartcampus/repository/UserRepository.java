package com.smartcampus.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.smartcampus.model.UserAccount;

public interface UserRepository extends MongoRepository<UserAccount,String> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);
}
