package com.nanobase.specai.compliance.application;

import com.nanobase.specai.analysis.domain.LifecycleStage;
import com.nanobase.specai.analysis.domain.Remediability;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemediabilityCalculator {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public RemediabilityCalculator(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    RemediabilityCalculator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Input(
        UUID organizationId,
        String capabilityType,
        String actionType,
        LocalDate bidDeadline,
        LifecycleStage lifecycleStage,
        Integer estimatedResolutionDays
    ) {
    }

    @Transactional(readOnly = true)
    public Remediability calculate(Input input) {
        if (input == null) {
            return Remediability.UNKNOWN;
        }
        int leadTime = input.estimatedResolutionDays() != null
            ? input.estimatedResolutionDays()
            : lookupLeadTime(input.organizationId(), input.capabilityType(), input.actionType());
        if (leadTime < 0) {
            return Remediability.UNKNOWN;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        if (input.bidDeadline() != null) {
            long daysUntilBid = ChronoUnit.DAYS.between(today, input.bidDeadline());
            if (daysUntilBid < leadTime) {
                if (input.lifecycleStage() == LifecycleStage.POST_AWARD
                    || input.lifecycleStage() == LifecycleStage.CONTRACT_EXECUTION
                    || input.lifecycleStage() == LifecycleStage.ONGOING) {
                    return Remediability.REMEDIABLE_AFTER_AWARD;
                }
                if (input.lifecycleStage() == LifecycleStage.PRE_CONTRACT) {
                    return Remediability.REMEDIABLE_BEFORE_CONTRACT;
                }
                return Remediability.HARD_BLOCKER;
            }
            return Remediability.REMEDIABLE_BEFORE_BID;
        }
        return switch (input.lifecycleStage()) {
            case POST_AWARD, CONTRACT_EXECUTION, ONGOING -> Remediability.REMEDIABLE_AFTER_AWARD;
            case PRE_CONTRACT -> Remediability.REMEDIABLE_BEFORE_CONTRACT;
            case CONTRACT_CLOSEOUT -> Remediability.NOT_APPLICABLE;
            default -> Remediability.UNKNOWN;
        };
    }

    private int lookupLeadTime(UUID organizationId, String capabilityType, String actionType) {
        Integer days = jdbc.query("""
            select average_days + approval_days as total_days
              from remediability_lead_time
             where capability_type = ?
               and action_type = ?
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc
             limit 1
            """, rs -> rs.next() ? rs.getInt("total_days") : null,
            capabilityType, actionType, organizationId);
        return days == null ? -1 : days;
    }
}
