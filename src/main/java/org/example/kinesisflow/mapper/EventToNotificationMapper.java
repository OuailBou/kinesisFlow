package org.example.kinesisflow.mapper;

import org.example.kinesisflow.record.Notification;
import org.example.kinesisflow.record.CryptoEvent;


public class EventToNotificationMapper {

    public static Notification mapToNotification(CryptoEvent event, String user) {
        return new Notification(
                event.asset(),
                event.price(),
                user
        );
    }
}
