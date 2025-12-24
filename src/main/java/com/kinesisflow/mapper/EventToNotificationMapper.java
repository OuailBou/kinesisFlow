package com.kinesisflow.mapper;

import com.kinesisflow.record.Notification;
import com.kinesisflow.record.CryptoEvent;


public class EventToNotificationMapper {

    public static Notification mapToNotification(CryptoEvent event, String user) {
        return new Notification(
                event.asset(),
                event.price(),
                user
        );
    }
}
