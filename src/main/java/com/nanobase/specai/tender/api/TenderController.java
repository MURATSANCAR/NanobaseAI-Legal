package com.nanobase.specai.tender.api;

import com.nanobase.specai.tender.api.TenderContracts.AddProjectMemberRequest;
import com.nanobase.specai.tender.api.TenderContracts.CreateTenderRequest;
import com.nanobase.specai.tender.api.TenderContracts.ProjectMemberResponse;
import com.nanobase.specai.tender.api.TenderContracts.TenderResponse;
import com.nanobase.specai.tender.api.TenderContracts.UpdateProjectMemberRequest;
import com.nanobase.specai.tender.api.TenderContracts.UpdateTenderRequest;
import com.nanobase.specai.tender.application.ProjectMemberService;
import com.nanobase.specai.tender.application.TenderProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenders")
public class TenderController {
    private final TenderProjectService service;
    private final ProjectMemberService memberService;

    public TenderController(TenderProjectService service, ProjectMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
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

    @PostMapping("/{id}/archive")
    TenderResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @GetMapping("/{id}/members")
    List<ProjectMemberResponse> members(@PathVariable UUID id) {
        return memberService.list(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectMemberResponse addMember(@PathVariable UUID id,
                                    @Valid @RequestBody AddProjectMemberRequest request) {
        return memberService.add(id, request);
    }

    @PutMapping("/{id}/members/{memberId}")
    ProjectMemberResponse updateMember(@PathVariable UUID id, @PathVariable UUID memberId,
                                       @Valid @RequestBody UpdateProjectMemberRequest request) {
        return memberService.update(id, memberId, request);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        memberService.remove(id, memberId);
    }
}
