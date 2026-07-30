package com.nanobase.specai.analysis.domain;

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
@Table(name = "requirement_condition")
public class RequirementCondition {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "requirement_id", nullable = false, updatable = false)
    private UUID requirementId;
    @Column(name = "condition_type", nullable = false, length = 80)
    private String conditionType;
    @Column(name = "field_name", length = 160)
    private String fieldName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConditionOperator operator;
    @Column(name = "expected_value", columnDefinition = "text")
    private String expectedValue;
    @Column(name = "expected_numeric_value")
    private BigDecimal expectedNumericValue;
    @Column(name = "expected_unit", length = 40)
    private String expectedUnit;
    @Column(name = "expected_date")
    private LocalDate expectedDate;
    @Column(name = "expected_boolean")
    private Boolean expectedBoolean;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected RequirementCondition() {
    }

    public static RequirementCondition create(UUID id, UUID organizationId, UUID requirementId,
                                              String conditionType, String fieldName,
                                              ConditionOperator operator, String expectedValue,
                                              BigDecimal expectedNumericValue, String expectedUnit,
                                              LocalDate expectedDate, Boolean expectedBoolean,
                                              int sequenceNo, boolean mandatory, Instant now) {
        RequirementCondition condition = new RequirementCondition();
        condition.id = id;
        condition.organizationId = organizationId;
        condition.requirementId = requirementId;
        condition.conditionType = conditionType == null ? "GENERIC" : conditionType;
        condition.fieldName = fieldName;
        condition.operator = operator;
        condition.expectedValue = expectedValue;
        condition.expectedNumericValue = expectedNumericValue;
        condition.expectedUnit = expectedUnit;
        condition.expectedDate = expectedDate;
        condition.expectedBoolean = expectedBoolean;
        condition.sequenceNo = sequenceNo;
        condition.mandatory = mandatory;
        condition.createdAt = now;
        condition.updatedAt = now;
        return condition;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID requirementId() { return requirementId; }
    public String conditionType() { return conditionType; }
    public String fieldName() { return fieldName; }
    public ConditionOperator operator() { return operator; }
    public String expectedValue() { return expectedValue; }
    public BigDecimal expectedNumericValue() { return expectedNumericValue; }
    public String expectedUnit() { return expectedUnit; }
    public LocalDate expectedDate() { return expectedDate; }
    public Boolean expectedBoolean() { return expectedBoolean; }
    public int sequenceNo() { return sequenceNo; }
    public boolean mandatory() { return mandatory; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
