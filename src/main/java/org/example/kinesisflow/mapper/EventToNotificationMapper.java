package org.example.kinesisflow.mapper;

import org.example.kinesisflow.record.Notification;
import org.example.kinesisflow.record.cryptoEvent;

import java.math.BigDecimal;

public class EventToNotificationMapper {

    public static Notification mapToNotification(cryptoEvent event, String user) {
        return new Notification(
                event.asset(),
                event.price(),
                user
        );
    }
}
