package com.securebank.notification.repository;

import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndChannelAndReadAtIsNull(UUID recipientUserId, Channel channel);
}
