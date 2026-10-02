package com.smartcampus.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartcampus.model.AuthSession;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.AuthSessionRepository;
import com.smartcampus.repository.UserRepository;

@Service
public class AuthService {
  private final UserRepository users;
  private final AuthSessionRepository sessions;

  @Value("${ADMIN_EMAIL:}") private String adminEmail;
  @Value("${ADMIN_PASSWORD:}") private String adminPassword;
  @Value("${ADMIN_NAME:Campus Administrator}") private String adminName;
  @Value("${ADMIN_SCHOOL:}") private String adminSchool;
  @Value("${ADMIN_CAMPUS:}") private String adminCampus;

  public AuthService(UserRepository users, AuthSessionRepository sessions) {
    this.users = users;
    this.sessions = sessions;
  }

  public Map<String,Object> register(Map<String,String> body) {
    String email = body.getOrDefault("email","").trim().toLowerCase();
    String password = body.getOrDefault("password","");
    String name = body.getOrDefault("name","").trim();
    if(name.isBlank() || email.isBlank() || password.length() < 8)
      throw new IllegalArgumentException("Name, valid email and 8+ character password are required");
    if(users.findByEmailIgnoreCase(email).isPresent())
      throw new IllegalArgumentException("Email already exists");

    UserAccount u = new UserAccount();
    u.setName(name);
    u.setEmail(email);
    u.setPasswordHash(hashPassword(password));
    u.setRole("STUDENT");
    u.setDepartment("");
    u.setSchoolName(body.getOrDefault("schoolName",""));
    u.setCampusName(body.getOrDefault("campusName",""));
    u.setSchoolType(body.getOrDefault("schoolType","University"));
    u.setSchoolState(body.getOrDefault("schoolState",""));
    users.save(u);
    return response(u, createToken(u));
  }

  public Map<String,Object> login(Map<String,String> body) {
    UserAccount u = users.findByEmailIgnoreCase(body.getOrDefault("email",""))
      .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
    if(!verifyPassword(body.getOrDefault("password",""), u.getPasswordHash()))
      throw new IllegalArgumentException("Invalid email or password");
    return response(u, createToken(u));
  }

  public UserAccount require(String header) {
    if(header == null || !header.startsWith("Bearer "))
      throw new IllegalArgumentException("Authentication required");
    AuthSession s = sessions.findByToken(header.substring(7))
      .orElseThrow(() -> new IllegalArgumentException("Invalid session"));
    if(s.getExpiresAt().isBefore(Instant.now()))
      throw new IllegalArgumentException("Session expired");
    return users.findById(s.getUserId())
      .orElseThrow(() -> new IllegalArgumentException("Account not found"));
  }

  public void logout(String header) {
    if(header != null && header.startsWith("Bearer "))
      sessions.findByToken(header.substring(7)).ifPresent(sessions::delete);
  }

  public void ensureBootstrapAdmin() {
    if(adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.length() < 8) return;
    if(users.findByEmailIgnoreCase(adminEmail).isPresent()) return;
    UserAccount u = new UserAccount();
    u.setName(adminName);
    u.setEmail(adminEmail.toLowerCase());
    u.setPasswordHash(hashPassword(adminPassword));
    u.setRole("ADMIN");
    u.setDepartment("Administration");
    u.setSchoolName(adminSchool);
    u.setCampusName(adminCampus);
    users.save(u);
  }

  private String createToken(UserAccount u) {
    AuthSession s = new AuthSession();
    s.setToken(UUID.randomUUID()+"."+UUID.randomUUID());
    s.setUserId(u.getId());
    s.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    sessions.save(s);
    return s.getToken();
  }

  private Map<String,Object> response(UserAccount u, String token) {
    return Map.of(
      "id",u.getId(),
      "name",u.getName(),
      "email",u.getEmail(),
      "role",u.getRole(),
      "schoolName",Optional.ofNullable(u.getSchoolName()).orElse(""),
      "campusName",Optional.ofNullable(u.getCampusName()).orElse(""),
      "token",token
    );
  }

  private String hashPassword(String password) {
    try {
      byte[] salt = new byte[16];
      new SecureRandom().nextBytes(salt);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] hash = factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120000, 256)).getEncoded();
      return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(hash);
    } catch(Exception e) {
      throw new IllegalStateException("Password hashing failed", e);
    }
  }

  private boolean verifyPassword(String password, String stored) {
    try {
      String[] parts = stored.split(":");
      if(parts.length != 2) return false;
      byte[] salt = Base64.getDecoder().decode(parts[0]);
      byte[] expected = Base64.getDecoder().decode(parts[1]);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] actual = factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120000, 256)).getEncoded();
      return MessageDigest.isEqual(expected, actual);
    } catch(Exception e) {
      return false;
    }
  }
}
