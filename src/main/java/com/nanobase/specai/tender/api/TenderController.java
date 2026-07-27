package com.nanobase.specai.tender.api;

import com.nanobase.specai.tender.api.TenderContracts.CreateTenderRequest;
import com.nanobase.specai.tender.api.TenderContracts.TenderResponse;
import com.nanobase.specai.tender.api.TenderContracts.UpdateTenderRequest;
import com.nanobase.specai.tender.application.TenderProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenders")
public class TenderController {
    private final TenderProjectService service;

    public TenderController(TenderProjectService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TenderResponse> create(@Valid @RequestBody CreateTenderRequest request) {
        TenderResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tenders/" + response.id())).body(response);
    }

    @GetMapping
    Page<TenderResponse> list(@PageableDefault(size = 25, sort = "createdAt") Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    TenderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    TenderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenderRequest request) {
        return service.update(id, request);
    }
}
