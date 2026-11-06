package com.smartcampus.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.smartcampus.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth){this.auth=auth;}

  @PostMapping("/register")
  public Map<String,Object> register(@RequestBody Map<String,String> body){return auth.register(body);}

  @PostMapping("/login")
  public Map<String,Object> login(@RequestBody Map<String,String> body){return auth.login(body);}

  @GetMapping("/me")
  public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String h){return auth.profile(h);}

  @PostMapping("/logout")
  public Map<String,Object> logout(@RequestHeader(value="Authorization",required=false) String h){
    auth.logout(h);
    return Map.of("ok",true);
  }
}
