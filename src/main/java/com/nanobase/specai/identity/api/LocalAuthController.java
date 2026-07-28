package com.nanobase.specai.identity.api;

import com.nanobase.specai.identity.api.LocalAuthContracts.LoginRequest;
import com.nanobase.specai.identity.api.LocalAuthContracts.LoginResponse;
import com.nanobase.specai.identity.application.LocalTokenService;
import com.nanobase.specai.identity.application.LocalTokenService.IssuedToken;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "specai.security.auth-mode", havingValue = "local", matchIfMissing = true)
public class LocalAuthController {
    private final LocalTokenService tokens;

    public LocalAuthController(LocalTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken issued = tokens.login(request.email(), request.password());
        return new LoginResponse(
            issued.accessToken(),
            "Bearer",
            issued.expiresInSeconds(),
            issued.email(),
            issued.displayName(),
            issued.roles(),
            issued.tenantId()
        );
    }
}
