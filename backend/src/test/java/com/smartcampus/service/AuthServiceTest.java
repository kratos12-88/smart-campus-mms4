package com.smartcampus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.smartcampus.config.AuthException;
import com.smartcampus.config.ConflictException;
import com.smartcampus.model.AuthSession;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.AuthSessionRepository;
import com.smartcampus.repository.UserRepository;

class AuthServiceTest {
  @Mock private UserRepository users;
  @Mock private AuthSessionRepository sessions;

  private AuthService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AuthService(users, sessions);
  }

  @Test
  void registerCreatesStudentAndSession() {
    when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());
    when(users.save(any(UserAccount.class))).thenAnswer(invocation -> {
      UserAccount u = invocation.getArgument(0);
      u.setId("user-1");
      return u;
    });
    when(sessions.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String,String> body = Map.of(
      "name", "Test Student",
      "email", " Student@Example.com ",
      "password", "Password123",
      "schoolName", " Example University ",
      "campusName", " Main Campus "
    );

    Map<String,Object> result = service.register(body);

    assertEquals("student@example.com", result.get("email"));
    assertEquals("STUDENT", result.get("role"));
    assertNotNull(result.get("token"));
    verify(users).save(any(UserAccount.class));
    verify(sessions).save(any(AuthSession.class));
  }

  @Test
  void registerRejectsMalformedEmail() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.register(Map.of(
      "name", "Test Student",
      "email", "not-an-email",
      "password", "Password123",
      "schoolName", "Example University",
      "campusName", "Main Campus"
    )));
    assertTrue(ex.getMessage().toLowerCase().contains("valid email"));
  }

  @Test
  void registerRejectsWeakPassword() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.register(Map.of(
      "name", "Test Student",
      "email", "student@example.com",
      "password", "password",
      "schoolName", "Example University",
      "campusName", "Main Campus"
    )));
    assertTrue(ex.getMessage().toLowerCase().contains("uppercase"));
  }

  @Test
  void registerRequiresSchoolAndCampus() {
    assertThrows(IllegalArgumentException.class, () -> service.register(Map.of(
      "name", "Test Student",
      "email", "student@example.com",
      "password", "Password123",
      "schoolName", "",
      "campusName", ""
    )));
  }

  @Test
  void registerRejectsDuplicateEmail() {
    UserAccount existing = new UserAccount();
    existing.setEmail("student@example.com");
    when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(existing));

    assertThrows(ConflictException.class, () -> service.register(Map.of(
      "name", "Test Student",
      "email", "student@example.com",
      "password", "Password123",
      "schoolName", "Example University",
      "campusName", "Main Campus"
    )));
  }

  @Test
  void loginNormalizesEmailAndRejectsWrongPassword() {
    UserAccount u = new UserAccount();
    u.setEmail("student@example.com");
    u.setPasswordHash("not-a-valid-hash");
    when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(u));

    assertThrows(AuthException.class, () -> service.login(Map.of(
      "email", " STUDENT@example.com ",
      "password", "WrongPassword1"
    )));
  }

  @Test
  void loginRejectsMissingCredentialsBeforeRepositoryLookup() {
    assertThrows(IllegalArgumentException.class, () -> service.login(Map.of(
      "email", "",
      "password", ""
    )));
    verifyNoInteractions(users);
  }

  @Test
  void requireReturnsAuthenticatedUser() {
    AuthSession session = new AuthSession();
    session.setToken("token-123");
    session.setUserId("user-1");
    session.setExpiresAt(Instant.now().plusSeconds(3600));

    UserAccount user = new UserAccount();
    user.setId("user-1");
    user.setName("Test Student");

    when(sessions.findByToken("token-123")).thenReturn(Optional.of(session));
    when(users.findById("user-1")).thenReturn(Optional.of(user));

    assertEquals("user-1", service.require("Bearer token-123").getId());
  }

  @Test
  void profileDoesNotExposePasswordHash() {
    AuthSession session = new AuthSession();
    session.setToken("token-123");
    session.setUserId("user-1");
    session.setExpiresAt(Instant.now().plusSeconds(3600));

    UserAccount user = new UserAccount();
    user.setId("user-1");
    user.setName("Test Student");
    user.setEmail("student@example.com");
    user.setRole("STUDENT");
    user.setPasswordHash("secret-hash");

    when(sessions.findByToken("token-123")).thenReturn(Optional.of(session));
    when(users.findById("user-1")).thenReturn(Optional.of(user));

    Map<String,Object> profile = service.profile("Bearer token-123");
    assertFalse(profile.containsKey("passwordHash"));
  }

  @Test
  void requireRejectsExpiredSessionAndDeletesIt() {
    AuthSession session = new AuthSession();
    session.setToken("expired");
    session.setUserId("user-1");
    session.setExpiresAt(Instant.now().minusSeconds(1));

    when(sessions.findByToken("expired")).thenReturn(Optional.of(session));

    assertThrows(AuthException.class, () -> service.require("Bearer expired"));
    verify(sessions).delete(session);
  }

  @Test
  void requireRejectsMalformedBearerHeader() {
    assertThrows(AuthException.class, () -> service.require("Token abc"));
  }

  @Test
  void logoutIsIdempotentForMissingHeader() {
    assertDoesNotThrow(() -> service.logout(null));
    verifyNoInteractions(sessions);
  }
}
