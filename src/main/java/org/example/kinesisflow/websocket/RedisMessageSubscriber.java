package org.example.kinesisflow.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.record.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;


@Service
public class RedisMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotifierWebSocketHandler notifierWebSocketHandler;
    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    public RedisMessageSubscriber(NotifierWebSocketHandler notifierWebSocketHandler) {
        this.notifierWebSocketHandler = notifierWebSocketHandler;
    }


    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody());

        try {

            Notification obj = objectMapper.readValue(json, Notification.class);
            log.info("Message from Pub/Sub received for the user: {}", obj.user());
            notifierWebSocketHandler.sendMessageToUser(obj.user(), json);
            System.out.println("Notification: " + obj.asset() + " " + obj.price());




        } catch (JsonProcessingException e) {

            log.error("Deserialization error in Redis Pub/Sub message. Message: '{}'", message, e);

        }
        catch (Exception e) {
            log.error("Unexpected error while processing Redis Pub/Sub notification. Message: '{}'", message, e);
        }
    }
}