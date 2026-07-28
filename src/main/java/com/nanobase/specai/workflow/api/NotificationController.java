package com.nanobase.specai.workflow.api;

import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.workflow.application.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final CurrentTenant currentTenant;

    public NotificationController(NotificationService notifications,
                                  CurrentTenant currentTenant) {
        this.notifications = notifications;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    List<Map<String, Object>> inbox() {
        return notifications.inbox(currentTenant.require().subject());
    }
}
