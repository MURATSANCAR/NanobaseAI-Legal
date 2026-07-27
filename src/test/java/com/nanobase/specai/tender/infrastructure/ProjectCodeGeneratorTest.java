package com.nanobase.specai.tender.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ProjectCodeGeneratorTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void formatsDatabaseSequenceWithYearAndSixDigits() {
        when(jdbc.queryForObject(
            "select nextval('tender_project_code_seq')", Long.class)).thenReturn(42L);
        ProjectCodeGenerator generator = new ProjectCodeGenerator(jdbc,
            Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC));

        assertThat(generator.next()).isEqualTo("TND-2026-000042");
    }
}
