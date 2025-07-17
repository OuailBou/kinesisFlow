package org.example.kinesisflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

@Service
public class RedisMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public RedisMessagePublisher(StringRedisTemplate redisTemplate) {
      this.redisTemplate = redisTemplate;
  }

    public void publish(String channel, Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize object to JSON", e);
        } catch (DataAccessException e) {
            throw e;
        }
    }
}
