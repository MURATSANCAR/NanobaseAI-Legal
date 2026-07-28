package com.nanobase.specai.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class LocalAuthContracts {
    private LocalAuthContracts() {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String email,
        String displayName,
        List<String> roles,
        String tenantId
    ) {
    }
}
