package com.nanobase.specai.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityContextMdcFilter mdcFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
                    .hasAnyRole("SYSTEM_ADMIN", "TENANT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER", "TECHNICAL_REVIEWER",
                    "REPORT_VIEWER")
                .requestMatchers(HttpMethod.POST, "/api/v1/tenders").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/tenders/*/documents",
                    "/api/v1/documents/*/versions",
                    "/api/v1/documents/*/reprocess").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/tenders/*/members",
                    "/api/v1/tenders/*/archive").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tenders/**").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tenders/*/members/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers("/api/v1/**").denyAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource ->
                resource.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
            .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .addFilterAfter(mdcFilter, BearerTokenAuthenticationFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${specai.security.allowed-origins:http://localhost:3000}") List<String> origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                roles.stream().map(String::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }
}
