package com.smartcampus.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartcampus.config.AuthException;
import com.smartcampus.config.ConflictException;
import com.smartcampus.model.AuthSession;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.AuthSessionRepository;
import com.smartcampus.repository.UserRepository;

@Service
public class AuthService {
  private static final Pattern EMAIL_PATTERN =
    Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME_PATTERN =
    Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,79}$");
  private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
  private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
  private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

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
    if(body == null) throw new IllegalArgumentException("Registration details are required");

    String name = clean(body.get("name"));
    String email = normalizeEmail(body.get("email"));
    String password = Optional.ofNullable(body.get("password")).orElse("");
    String confirmPassword = Optional.ofNullable(body.get("confirmPassword")).orElse("");
    String schoolName = clean(body.get("schoolName"));
    String campusName = clean(body.get("campusName"));
    String schoolType = clean(body.getOrDefault("schoolType","University"));
    String schoolState = clean(body.get("schoolState"));

    validateName(name);
    validateEmail(email);
    validatePassword(password);
    if(confirmPassword.isBlank()) throw new IllegalArgumentException("Please confirm your password");
    if(!password.equals(confirmPassword)) throw new IllegalArgumentException("Passwords do not match");
    requireText(schoolName, "School is required", 2, 120);
    requireText(campusName, "Campus is required", 2, 120);
    if(!schoolType.isBlank()) requireText(schoolType, "School type is invalid", 2, 60);
    if(!schoolState.isBlank() && schoolState.length() > 80)
      throw new IllegalArgumentException("School state must be 80 characters or fewer");

    if(users.findByEmailIgnoreCase(email).isPresent())
      throw new ConflictException("An account with this email already exists");

    UserAccount u = new UserAccount();
    u.setName(name);
    u.setEmail(email);
    u.setPasswordHash(hashPassword(password));
    u.setRole("STUDENT");
    u.setDepartment("");
    u.setSchoolName(schoolName);
    u.setCampusName(campusName);
    u.setSchoolType(schoolType.isBlank() ? "University" : schoolType);
    u.setSchoolState(schoolState);
    users.save(u);
    return response(u, createToken(u));
  }

  public Map<String,Object> login(Map<String,String> body) {
    if(body == null) throw new IllegalArgumentException("Email and password are required");

    String email = normalizeEmail(body.get("email"));
    String password = Optional.ofNullable(body.get("password")).orElse("");

    if(email.isBlank() || password.isBlank())
      throw new IllegalArgumentException("Email and password are required");
    validateEmail(email);

    UserAccount u = users.findByEmailIgnoreCase(email)
      .orElseThrow(() -> new AuthException("Invalid email or password"));

    if(u.getPasswordHash() == null || !verifyPassword(password, u.getPasswordHash()))
      throw new AuthException("Invalid email or password");

    return response(u, createToken(u));
  }

  public UserAccount require(String header) {
    String token = bearerToken(header);
    AuthSession s = sessions.findByToken(token)
      .orElseThrow(() -> new AuthException("Invalid or expired session"));

    if(s.getExpiresAt() == null || !s.getExpiresAt().isAfter(Instant.now())) {
      sessions.delete(s);
      throw new AuthException("Session expired. Please sign in again");
    }

    return users.findById(s.getUserId())
      .orElseThrow(() -> new AuthException("Account no longer exists"));
  }

  public Map<String,Object> profile(String header) {
    return safeProfile(require(header));
  }

  public void logout(String header) {
    if(header == null || header.isBlank()) return;
    try {
      String token = bearerToken(header);
      sessions.findByToken(token).ifPresent(sessions::delete);
    } catch(AuthException ignored) {
    }
  }

  public void ensureBootstrapAdmin() {
    String email = normalizeEmail(adminEmail);
    if(email.isBlank() || adminPassword == null || adminPassword.length() < 8) return;
    if(!EMAIL_PATTERN.matcher(email).matches()) return;
    if(users.findByEmailIgnoreCase(email).isPresent()) return;

    UserAccount u = new UserAccount();
    u.setName(clean(adminName).isBlank() ? "Campus Administrator" : clean(adminName));
    u.setEmail(email);
    u.setPasswordHash(hashPassword(adminPassword));
    u.setRole("ADMIN");
    u.setDepartment("Administration");
    u.setSchoolName(clean(adminSchool));
    u.setCampusName(clean(adminCampus));
    users.save(u);
  }

  private String createToken(UserAccount u) {
    if(u.getId() == null || u.getId().isBlank())
      throw new IllegalStateException("Unable to create session for unsaved account");

    AuthSession s = new AuthSession();
    s.setToken(UUID.randomUUID()+"."+UUID.randomUUID());
    s.setUserId(u.getId());
    s.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    sessions.save(s);
    return s.getToken();
  }

  private Map<String,Object> response(UserAccount u, String token) {
    Map<String,Object> profile = safeProfile(u);
    return Map.of(
      "id", profile.get("id"),
      "name", profile.get("name"),
      "email", profile.get("email"),
      "role", profile.get("role"),
      "schoolName", profile.get("schoolName"),
      "campusName", profile.get("campusName"),
      "token", token
    );
  }

  private Map<String,Object> safeProfile(UserAccount u) {
    return Map.of(
      "id", Optional.ofNullable(u.getId()).orElse(""),
      "name", Optional.ofNullable(u.getName()).orElse(""),
      "email", Optional.ofNullable(u.getEmail()).orElse(""),
      "role", Optional.ofNullable(u.getRole()).orElse(""),
      "schoolName", Optional.ofNullable(u.getSchoolName()).orElse(""),
      "campusName", Optional.ofNullable(u.getCampusName()).orElse("")
    );
  }

  private String bearerToken(String header) {
    if(header == null || !header.startsWith("Bearer "))
      throw new AuthException("Authentication required");

    String token = header.substring(7).trim();
    if(token.isBlank() || token.length() > 200 || token.contains(" "))
      throw new AuthException("Invalid session");

    return token;
  }

  private void validateName(String name) {
    if(name.isBlank()) throw new IllegalArgumentException("Full name is required");
    if(!NAME_PATTERN.matcher(name).matches())
      throw new IllegalArgumentException("Enter a valid full name between 2 and 80 characters");
  }

  private void validateEmail(String email) {
    if(email.isBlank()) throw new IllegalArgumentException("Email is required");
    if(email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches())
      throw new IllegalArgumentException("Enter a valid email address");
  }

  private void validatePassword(String password) {
    if(password.length() < 8 || password.length() > 128)
      throw new IllegalArgumentException("Password must be between 8 and 128 characters");
    if(!UPPER.matcher(password).matches() || !LOWER.matcher(password).matches() || !DIGIT.matcher(password).matches())
      throw new IllegalArgumentException("Password must include uppercase, lowercase, and a number");
  }

  private void requireText(String value, String message, int min, int max) {
    if(value == null || value.length() < min || value.length() > max)
      throw new IllegalArgumentException(message);
  }

  private String normalizeEmail(String value) {
    return clean(value).toLowerCase();
  }

  private String clean(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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
