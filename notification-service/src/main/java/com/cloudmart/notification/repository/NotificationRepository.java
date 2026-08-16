package com.cloudmart.notification.repository;

import com.cloudmart.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop200ByOrderBySentAtDesc();
}
