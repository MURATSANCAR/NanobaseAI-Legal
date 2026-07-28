package com.nanobase.specai.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
        @Value("${specai.security.jwt.audience:specai-api}") String audience,
        @Value("${specai.security.jwt.clock-skew-seconds:60}") long clockSkewSeconds
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(
            Duration.ofSeconds(clockSkewSeconds));
        JwtClaimValidator<List<String>> audienceValidator = new JwtClaimValidator<>(
            "aud", values -> values != null && values.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
            timestamp, new JwtIssuerValidator(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityContextMdcFilter mdcFilter,
                                            RateLimitFilter rateLimitFilter,
                                            TenantTransactionFilter tenantTransactionFilter)
        throws Exception {
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
                    "/api/v1/documents/*/reprocess",
                    "/api/v1/processing-jobs/*/cancel",
                    "/api/v1/documents/*/requirement-extractions",
                    "/api/v1/requirement-extractions/*/cancel",
                    "/api/v1/requirement-extractions/*/reprocess",
                    "/api/v1/documents/*/knowledge-extractions",
                    "/api/v1/knowledge-extractions/*/cancel",
                    "/api/v1/tenders/*/compliance-analyses",
                    "/api/v1/compliance-analyses/*/cancel",
                    "/api/v1/compliance-analyses/*/retry-failed",
                    "/api/v1/knowledge/entities",
                    "/api/v1/knowledge/entities/*/merge",
                    "/api/v1/knowledge/entities/*/split",
                    "/api/v1/knowledge/entities/*/attributes",
                    "/api/v1/knowledge/entities/*/capabilities",
                    "/api/v1/knowledge/relations",
                    "/api/v1/evidence/*/verify",
                    "/api/v1/evidence/*/invalidate",
                    "/api/v1/compliance-evaluations/*/review",
                    "/api/v1/compliance-evaluations/*/evidence",
                    "/api/v1/tenders/*/risk-analyses",
                    "/api/v1/risk-analyses/*/cancel",
                    "/api/v1/risk-analyses/*/retry-failed",
                    "/api/v1/analysis-profiles/preview").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                        "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/requirements/*/review",
                    "/api/v1/requirements/*/split",
                    "/api/v1/requirements/*/merge",
                    "/api/v1/risks/*/review",
                    "/api/v1/risks/*/assign",
                    "/api/v1/risks/*/mitigations",
                    "/api/v1/ambiguities/*/review",
                    "/api/v1/ambiguities/*/interpretations",
                    "/api/v1/ambiguities/*/clarification-candidates",
                    "/api/v1/conflicts/*/review",
                    "/api/v1/conflicts/*/resolve",
                    "/api/v1/conflicts/*/clarification-candidates",
                    "/api/v1/documents/*/change-sets",
                    "/api/v1/change-sets/*/impact-analyses").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                        "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/terminology-catalogs/*/candidate-terms",
                    "/api/v1/terminology-entries/*/approve",
                    "/api/v1/terminology-entries/*/reject",
                    "/api/v1/workflows",
                    "/api/v1/workflows/*/versions",
                    "/api/v1/workflow-versions/*/validate",
                    "/api/v1/workflow-versions/*/simulate",
                    "/api/v1/workflow-versions/*/activate",
                    "/api/v1/report-definitions",
                    "/api/v1/report-definitions/*/versions",
                    "/api/v1/report-definition-versions/*/activate").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/workflow-instances",
                    "/api/v1/workflow-instances/*/cancel",
                    "/api/v1/workflow-instances/*/retry",
                    "/api/v1/tasks/*/claim",
                    "/api/v1/tasks/*/assign",
                    "/api/v1/tasks/*/complete",
                    "/api/v1/tasks/*/block",
                    "/api/v1/tasks/*/escalate",
                    "/api/v1/tasks/*/comments",
                    "/api/v1/approvals/*/decisions",
                    "/api/v1/clarifications/*/revisions",
                    "/api/v1/clarifications/*/submit-review",
                    "/api/v1/clarifications/*/approve",
                    "/api/v1/clarifications/*/mark-sent",
                    "/api/v1/clarifications/*/answers",
                    "/api/v1/report-definition-versions/*/preview",
                    "/api/v1/tenders/*/reports",
                    "/api/v1/tenders/*/decision-support-cases",
                    "/api/v1/decision-support-cases/*/decisions",
                    "/api/v1/tenders/*/finalize",
                    "/api/v1/tenders/*/reopen").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                        "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/tenders/*/members",
                    "/api/v1/tenders/*/archive").hasAnyRole(
                        "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tenders/**").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/requirements/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                    "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/risks/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                    "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/change-sets/*/items/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                    "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/knowledge/**").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                    "TECHNICAL_REVIEWER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tenders/*/members/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER")
                .requestMatchers(HttpMethod.DELETE,
                    "/api/v1/knowledge/**",
                    "/api/v1/compliance-evaluations/*/evidence/*").hasAnyRole(
                    "SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER",
                    "TECHNICAL_REVIEWER")
                .requestMatchers("/api/v1/**").denyAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource ->
                resource.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
            .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .addFilterAfter(mdcFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, SecurityContextMdcFilter.class)
            .addFilterAfter(tenantTransactionFilter, RateLimitFilter.class)
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
