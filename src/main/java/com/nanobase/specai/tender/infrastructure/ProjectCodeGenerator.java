package com.nanobase.specai.tender.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProjectCodeGenerator {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ProjectCodeGenerator(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ProjectCodeGenerator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public String next() {
        Long sequence = jdbc.queryForObject(
            "select nextval('tender_project_code_seq')", Long.class);
        if (sequence == null) {
            throw new IllegalStateException("Project code sequence did not return a value");
        }
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        return "TND-%d-%06d".formatted(year, sequence);
    }
}
