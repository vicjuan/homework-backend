package com.example.demo.repository;

import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByEventStatus(EventStatus eventStatus);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.eventStatus = :to WHERE n.eventStatus = :from")
    int transitionEventStatus(@Param("from") EventStatus from, @Param("to") EventStatus to);
}
