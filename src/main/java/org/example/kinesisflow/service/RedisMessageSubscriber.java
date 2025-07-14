package org.example.kinesisflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.record.Notification;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;


@Service
public class RedisMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotifierWebSocketHandler  notifierWebSocketHandler;

    public RedisMessageSubscriber(NotifierWebSocketHandler notifierWebSocketHandler) {
        this.notifierWebSocketHandler = notifierWebSocketHandler;
    }


    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody());

        try {
            Notification obj = objectMapper.readValue(json, Notification.class);
            notifierWebSocketHandler.sendMessageToUser(obj.user(), json);
            System.out.println("Notification: " + obj.asset() + " " + obj.price());




        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}