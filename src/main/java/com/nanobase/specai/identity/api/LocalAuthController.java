package com.nanobase.specai.identity.api;

import com.nanobase.specai.identity.api.LocalAuthContracts.LoginRequest;
import com.nanobase.specai.identity.api.LocalAuthContracts.LoginResponse;
import com.nanobase.specai.identity.application.LocalTokenService;
import com.nanobase.specai.identity.application.LocalTokenService.IssuedToken;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "specai.security.auth-mode", havingValue = "local", matchIfMissing = true)
public class LocalAuthController {
    private final LocalTokenService tokens;
    private final boolean autoLogin;

    public LocalAuthController(
        LocalTokenService tokens,
        @Value("${specai.security.auto-login:false}") boolean autoLogin
    ) {
        this.tokens = tokens;
        this.autoLogin = autoLogin;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken issued = tokens.login(request.email(), request.password());
        return toResponse(issued);
    }

    /** Passwordless admin session for EasyMeeting / portal demos. */
    @PostMapping("/auto-login")
    LoginResponse autoLogin() {
        if (!autoLogin) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toResponse(tokens.issueBootstrapAdmin());
    }

    private static LoginResponse toResponse(IssuedToken issued) {
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
