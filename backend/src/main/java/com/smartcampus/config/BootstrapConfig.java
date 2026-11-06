package com.smartcampus.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.smartcampus.service.AuthService;

@Configuration
public class BootstrapConfig {
  @Bean
  CommandLineRunner bootstrapAccounts(AuthService auth) {
    return args -> {
      auth.ensureBootstrapAdmin();
      auth.ensureBootstrapDemoStudent();
    };
  }
}
