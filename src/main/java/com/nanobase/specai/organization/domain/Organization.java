package com.nanobase.specai.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization")
public class Organization {
    @Id
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Organization() {
    }

    public Organization(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }
}
