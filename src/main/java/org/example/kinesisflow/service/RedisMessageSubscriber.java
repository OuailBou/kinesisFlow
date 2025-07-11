package org.example.kinesisflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.record.Notification;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;


@Service
public class RedisMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = message.toString();

        try {
            Notification obj = objectMapper.readValue(json, Notification.class);
            System.out.println("Object received: " + obj);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}