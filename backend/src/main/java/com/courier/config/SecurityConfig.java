package com.courier.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
   http.csrf(csrf->csrf.disable()).cors(cors->{}).authorizeHttpRequests(a->a.anyRequest().permitAll());
   return http.build();
 }
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}