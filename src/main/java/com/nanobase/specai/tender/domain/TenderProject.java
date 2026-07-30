package com.nanobase.specai.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
    @Column(length = 2)
    private String country;
    @Column(name = "estimated_value")
    private BigDecimal estimatedValue;
    @Column(name = "publication_date")
    private LocalDate publicationDate;
    @Column(name = "award_date")
    private LocalDate awardDate;
    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;
    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;
    @Column(name = "business_owner_user_id", length = 255)
    private String businessOwnerUserId;
    @Column(name = "legal_owner_user_id", length = 255)
    private String legalOwnerUserId;
    @Column(name = "technical_owner_user_id", length = 255)
    private String technicalOwnerUserId;
    @Column(name = "financial_owner_user_id", length = 255)
    private String financialOwnerUserId;
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
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
        update(name, institutionName, tenderRegistrationNumber, tenderType, businessType, sector,
            priority, bidDeadline, clarificationDeadline, description, currency,
            this.country, this.estimatedValue, this.publicationDate, this.awardDate,
            this.contractStartDate, this.contractEndDate, this.businessOwnerUserId,
            this.legalOwnerUserId, this.technicalOwnerUserId, this.financialOwnerUserId,
            null, now);
    }

    public void update(String name, String institutionName, String tenderRegistrationNumber,
                       String tenderType, String businessType, String sector, Priority priority,
                       LocalDate bidDeadline, LocalDate clarificationDeadline, String description,
                       String currency, String country, BigDecimal estimatedValue,
                       LocalDate publicationDate, LocalDate awardDate,
                       LocalDate contractStartDate, LocalDate contractEndDate,
                       String businessOwnerUserId, String legalOwnerUserId,
                       String technicalOwnerUserId, String financialOwnerUserId,
                       String updatedBy, Instant now) {
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
        this.country = country;
        this.estimatedValue = estimatedValue;
        this.publicationDate = publicationDate;
        this.awardDate = awardDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.businessOwnerUserId = businessOwnerUserId;
        this.legalOwnerUserId = legalOwnerUserId;
        this.technicalOwnerUserId = technicalOwnerUserId;
        this.financialOwnerUserId = financialOwnerUserId;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public void transitionTo(TenderStatus next, String actor, Instant now) {
        this.status = next;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (status != TenderStatus.ARCHIVED && status != TenderStatus.CLOSED) {
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
    public String country() { return country; }
    public BigDecimal estimatedValue() { return estimatedValue; }
    public LocalDate publicationDate() { return publicationDate; }
    public LocalDate awardDate() { return awardDate; }
    public LocalDate contractStartDate() { return contractStartDate; }
    public LocalDate contractEndDate() { return contractEndDate; }
    public String businessOwnerUserId() { return businessOwnerUserId; }
    public String legalOwnerUserId() { return legalOwnerUserId; }
    public String technicalOwnerUserId() { return technicalOwnerUserId; }
    public String financialOwnerUserId() { return financialOwnerUserId; }
    public String updatedBy() { return updatedBy; }
    public String ownerUserId() { return ownerUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
