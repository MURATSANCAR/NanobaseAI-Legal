package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitConversionService {
    private final JdbcTemplate jdbc;

    public UnitConversionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ConversionResult convert(UUID organizationId, BigDecimal value,
                                    UUID sourceConceptId, UUID targetConceptId) {
        Unit source = unit(organizationId, sourceConceptId);
        Unit target = unit(organizationId, targetConceptId);
        if (source.dimension() == null || !source.dimension().equals(target.dimension())) {
            return new ConversionResult(false, null, source.dimension(), target.dimension());
        }
        BigDecimal base = value.multiply(source.factor(), MathContext.DECIMAL128)
            .add(source.offset(), MathContext.DECIMAL128);
        BigDecimal converted = base.subtract(target.offset(), MathContext.DECIMAL128)
            .divide(target.factor(), MathContext.DECIMAL128);
        return new ConversionResult(true, converted, source.dimension(), target.dimension());
    }

    private Unit unit(UUID organizationId, UUID conceptId) {
        return jdbc.query("""
            select dimension, conversion_metadata_json::text
            from measurement_unit
            where concept_id = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc
            limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Unit concept is not configured");
                }
                try {
                    JsonNode metadata = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(result.getString(2));
                    return new Unit(result.getString(1),
                        metadata.path("factor").decimalValue().compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ONE : metadata.path("factor").decimalValue(),
                        metadata.path("offset").decimalValue());
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalStateException("Unit conversion metadata is invalid",
                        exception);
                }
            }, conceptId, organizationId);
    }

    public record ConversionResult(boolean compatible, BigDecimal value,
                                   String sourceDimension, String targetDimension) {
    }

    private record Unit(String dimension, BigDecimal factor, BigDecimal offset) {
    }
}
