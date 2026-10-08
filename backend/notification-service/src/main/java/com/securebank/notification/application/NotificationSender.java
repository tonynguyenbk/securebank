package com.securebank.notification.application;

import com.securebank.notification.domain.Notification;

/**
 * Port for out-of-app delivery (EMAIL, SMS). A real provider adapter would implement this; the portfolio build
 * ships a simulated one. Throwing marks the notification FAILED.
 */
public interface NotificationSender {

    void send(Notification notification);
}
