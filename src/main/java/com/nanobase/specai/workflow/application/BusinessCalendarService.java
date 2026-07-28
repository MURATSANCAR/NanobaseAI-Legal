package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.BusinessCalendarDefinition;
import com.nanobase.specai.workflow.application.WorkflowModels.SlaCalculationResult;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BusinessCalendarService {
    private static final int MAX_ITERATIONS = 2_000_000;

    public SlaCalculationResult calculate(Instant start, long businessMinutes,
                                          double warningRatio,
                                          BusinessCalendarDefinition calendar) {
        if (businessMinutes < 0 || warningRatio < 0 || warningRatio > 1) {
            throw new IllegalArgumentException("Invalid SLA duration or warning ratio");
        }
        Instant target = addBusinessMinutes(start, businessMinutes, calendar);
        Instant warning = addBusinessMinutes(start,
            Math.round(businessMinutes * warningRatio), calendar);
        return new SlaCalculationResult(target, warning, target);
    }

    public Instant addBusinessMinutes(Instant start, long minutes,
                                      BusinessCalendarDefinition calendar) {
        ZoneId zone = ZoneId.of(calendar.timezone());
        JsonNode config = calendar.configuration();
        LocalTime workStart = LocalTime.parse(config.path("workdayStart").asText("09:00"));
        LocalTime workEnd = LocalTime.parse(config.path("workdayEnd").asText("17:00"));
        if (!workEnd.isAfter(workStart)) {
            throw new IllegalArgumentException("Business calendar workdayEnd must be after start");
        }
        Set<DayOfWeek> workingDays = workingDays(config.path("workingDays"));
        ZonedDateTime cursor = start.atZone(zone).withSecond(0).withNano(0);
        long remaining = minutes;
        int iterations = 0;
        while (remaining > 0) {
            if (++iterations > MAX_ITERATIONS) {
                throw new IllegalArgumentException("SLA calculation exceeds safety limit");
            }
            LocalDate date = cursor.toLocalDate();
            if (!workingDays.contains(cursor.getDayOfWeek())
                || isClosed(date, calendar)) {
                cursor = nextDayStart(cursor, workStart);
                continue;
            }
            if (cursor.toLocalTime().isBefore(workStart)) {
                cursor = ZonedDateTime.of(date, workStart, zone);
            } else if (!cursor.toLocalTime().isBefore(workEnd)) {
                cursor = nextDayStart(cursor, workStart);
                continue;
            }
            long available = Duration.between(cursor, ZonedDateTime.of(date, workEnd, zone))
                .toMinutes();
            long consumed = Math.min(available, remaining);
            cursor = cursor.plusMinutes(consumed);
            remaining -= consumed;
            if (remaining > 0) {
                cursor = nextDayStart(cursor, workStart);
            }
        }
        return cursor.toInstant();
    }

    private static boolean isClosed(LocalDate date, BusinessCalendarDefinition calendar) {
        String exception = calendar.exceptions().get(date);
        return exception != null && !"WORKING_DAY".equals(exception);
    }

    private static ZonedDateTime nextDayStart(ZonedDateTime value, LocalTime start) {
        LocalDateTime local = LocalDateTime.of(value.toLocalDate().plusDays(1), start);
        return local.atZone(value.getZone());
    }

    private static Set<DayOfWeek> workingDays(JsonNode node) {
        Set<DayOfWeek> days = new HashSet<>();
        if (node.isArray()) {
            node.forEach(value -> days.add(DayOfWeek.valueOf(value.asText())));
        }
        if (days.isEmpty()) {
            days.addAll(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        }
        return Set.copyOf(days);
    }
}
