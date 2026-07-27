package com.nanobase.specai.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tender_project")
public class TenderProject {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(nullable = false, unique = true, updatable = false, length = 32)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "contracting_authority", nullable = false, length = 200)
    private String contractingAuthority;
    @Column(name = "registration_number", length = 100)
    private String registrationNumber;
    @Column(name = "deadline")
    private LocalDate deadline;
    @Column(length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenderStatus status;
    @Column(length = 4000)
    private String description;
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected TenderProject() {
    }

    public static TenderProject create(UUID id, UUID tenantId, String code, String name,
                                       String contractingAuthority, String registrationNumber,
                                       LocalDate deadline, String currency, Priority priority,
                                       String description, String actor, Instant now) {
        TenderProject project = new TenderProject();
        project.id = id;
        project.tenantId = tenantId;
        project.code = code;
        project.name = name;
        project.contractingAuthority = contractingAuthority;
        project.registrationNumber = registrationNumber;
        project.deadline = deadline;
        project.currency = currency;
        project.priority = priority;
        project.status = TenderStatus.DRAFT;
        project.description = description;
        project.createdBy = actor;
        project.createdAt = now;
        project.updatedAt = now;
        return project;
    }

    public void update(String name, String contractingAuthority, String registrationNumber,
                       LocalDate deadline, String currency, Priority priority, String description, Instant now) {
        this.name = name;
        this.contractingAuthority = contractingAuthority;
        this.registrationNumber = registrationNumber;
        this.deadline = deadline;
        this.currency = currency;
        this.priority = priority;
        this.description = description;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public String contractingAuthority() { return contractingAuthority; }
    public String registrationNumber() { return registrationNumber; }
    public LocalDate deadline() { return deadline; }
    public String currency() { return currency; }
    public Priority priority() { return priority; }
    public TenderStatus status() { return status; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
