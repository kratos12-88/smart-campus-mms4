package com.smartcampus.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("sessions")
public class AuthSession {
  @Id private String id;
  @Indexed(unique=true) private String token;
  private String userId;
  @Indexed(expireAfterSeconds=0) private Instant expiresAt;

  public String getId(){return id;} public void setId(String v){id=v;}
  public String getToken(){return token;} public void setToken(String v){token=v;}
  public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
  public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
}
