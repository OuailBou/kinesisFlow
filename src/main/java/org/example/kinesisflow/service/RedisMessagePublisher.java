package org.example.kinesisflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
