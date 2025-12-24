package com.kinesisflow.service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RedisStringService {

    private final RedisTemplate<String, Double> redisTemplateDouble;

    public RedisStringService(RedisTemplate<String, Double> redisTemplateDouble) {
        this.redisTemplateDouble = redisTemplateDouble;
    }

    public void save(String key, BigDecimal value) {
        redisTemplateDouble.opsForValue().set(key, value.doubleValue());
    }

    public Double get(String key) {
        return redisTemplateDouble.opsForValue().get(key);
    }
    public void deleteAll() {
        var keys = redisTemplateDouble.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplateDouble.delete(keys);
        }
    }

}
