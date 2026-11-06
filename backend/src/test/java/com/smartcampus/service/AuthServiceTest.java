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
      "email", "Student@Example.com",
      "password", "password123",
      "schoolName", "Example University",
      "campusName", "Main Campus"
    );

    Map<String,Object> result = service.register(body);

    assertEquals("student@example.com", result.get("email"));
    assertEquals("STUDENT", result.get("role"));
    assertNotNull(result.get("token"));
    verify(users).save(any(UserAccount.class));
    verify(sessions).save(any(AuthSession.class));
  }

  @Test
  void registerRejectsDuplicateEmail() {
    UserAccount existing = new UserAccount();
    existing.setEmail("student@example.com");
    when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(existing));

    assertThrows(IllegalArgumentException.class, () -> service.register(Map.of(
      "name", "Test Student",
      "email", "student@example.com",
      "password", "password123"
    )));
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
  void requireRejectsExpiredSession() {
    AuthSession session = new AuthSession();
    session.setToken("expired");
    session.setUserId("user-1");
    session.setExpiresAt(Instant.now().minusSeconds(1));

    when(sessions.findByToken("expired")).thenReturn(Optional.of(session));

    assertThrows(IllegalArgumentException.class, () -> service.require("Bearer expired"));
  }
}
