package com.cloudmart.notification.controller;

import com.cloudmart.notification.store.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// NOTE: no @CrossOrigin here on purpose - see ProductController (in
// product-service) for why (CORS is handled once, centrally, by api-gateway).
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStore notificationStore;

    @GetMapping
    public List<NotificationStore.NotificationRecord> recent() {
        return notificationStore.recent();
    }
}
