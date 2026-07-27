package com.nanobase.specai.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").hasAuthority("SCOPE_specai.admin")
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority("SCOPE_specai.read", "SCOPE_specai.write")
                .requestMatchers("/api/v1/**").hasAuthority("SCOPE_specai.write")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(withDefaults()))
            .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .build();
    }
}
