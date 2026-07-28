package com.nanobase.specai.release.api;

import com.nanobase.specai.release.api.ReleaseContracts.DiagnosticBundleResponse;
import com.nanobase.specai.release.api.ReleaseContracts.SystemVersionResponse;
import com.nanobase.specai.release.application.SystemInformationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SystemInformationController {
    private final SystemInformationService system;

    public SystemInformationController(SystemInformationService system) {
        this.system = system;
    }

    @GetMapping("/system/version")
    SystemVersionResponse version() {
        return system.version();
    }

    @PostMapping("/operations/diagnostic-bundles")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    DiagnosticBundleResponse diagnosticBundle() {
        return system.diagnosticBundle();
    }
}
