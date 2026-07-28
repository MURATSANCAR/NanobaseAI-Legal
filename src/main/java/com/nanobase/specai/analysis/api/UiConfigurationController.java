package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisCatalogPort;
import com.nanobase.specai.shared.security.CurrentTenant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ui-configurations")
public class UiConfigurationController {
    private final AnalysisCatalogPort catalog;
    private final CurrentTenant currentTenant;

    public UiConfigurationController(AnalysisCatalogPort catalog,
                                     CurrentTenant currentTenant) {
        this.catalog = catalog;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/requirement-grid")
    JsonNode requirementGrid() {
        return catalog.requirementGrid(currentTenant.require().tenantId());
    }
}
