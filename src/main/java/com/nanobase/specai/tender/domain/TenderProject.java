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
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_code", nullable = false, updatable = false, length = 32)
    private String projectCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "institution_name", nullable = false, length = 200)
    private String institutionName;
    @Column(name = "tender_registration_number", length = 100)
    private String tenderRegistrationNumber;
    @Column(name = "tender_type", length = 80)
    private String tenderType;
    @Column(name = "business_type", length = 80)
    private String businessType;
    @Column(length = 120)
    private String sector;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenderStatus status;
    @Column(name = "bid_deadline")
    private LocalDate bidDeadline;
    @Column(name = "clarification_deadline")
    private LocalDate clarificationDeadline;
    @Column(length = 4000)
    private String description;
    @Column(name = "owner_user_id", nullable = false, updatable = false, length = 255)
    private String ownerUserId;
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;
    @Column(name = "currency", length = 3)
    private String currency;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected TenderProject() {
    }

    public static TenderProject create(UUID id, UUID organizationId, String projectCode, String name,
                                       String institutionName, String tenderRegistrationNumber,
                                       String tenderType, String businessType, String sector,
                                       Priority priority, LocalDate bidDeadline,
                                       LocalDate clarificationDeadline, String description,
                                       String currency, String ownerUserId, Instant now) {
        TenderDates.validate(bidDeadline, clarificationDeadline);
        TenderProject project = new TenderProject();
        project.id = id;
        project.organizationId = organizationId;
        project.projectCode = projectCode;
        project.name = name;
        project.institutionName = institutionName;
        project.tenderRegistrationNumber = tenderRegistrationNumber;
        project.tenderType = tenderType;
        project.businessType = businessType;
        project.sector = sector;
        project.priority = priority;
        project.status = TenderStatus.DRAFT;
        project.bidDeadline = bidDeadline;
        project.clarificationDeadline = clarificationDeadline;
        project.description = description;
        project.currency = currency;
        project.ownerUserId = ownerUserId;
        project.createdBy = ownerUserId;
        project.createdAt = now;
        project.updatedAt = now;
        return project;
    }

    public void update(String name, String institutionName, String tenderRegistrationNumber,
                       String tenderType, String businessType, String sector, Priority priority,
                       LocalDate bidDeadline, LocalDate clarificationDeadline, String description,
                       String currency, Instant now) {
        TenderDates.validate(bidDeadline, clarificationDeadline);
        this.name = name;
        this.institutionName = institutionName;
        this.tenderRegistrationNumber = tenderRegistrationNumber;
        this.tenderType = tenderType;
        this.businessType = businessType;
        this.sector = sector;
        this.priority = priority;
        this.bidDeadline = bidDeadline;
        this.clarificationDeadline = clarificationDeadline;
        this.description = description;
        this.currency = currency;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (status != TenderStatus.ARCHIVED) {
            status = TenderStatus.ARCHIVED;
            updatedAt = now;
        }
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String projectCode() { return projectCode; }
    public String name() { return name; }
    public String institutionName() { return institutionName; }
    public String tenderRegistrationNumber() { return tenderRegistrationNumber; }
    public String tenderType() { return tenderType; }
    public String businessType() { return businessType; }
    public String sector() { return sector; }
    public Priority priority() { return priority; }
    public TenderStatus status() { return status; }
    public LocalDate bidDeadline() { return bidDeadline; }
    public LocalDate clarificationDeadline() { return clarificationDeadline; }
    public String description() { return description; }
    public String currency() { return currency; }
    public String ownerUserId() { return ownerUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
