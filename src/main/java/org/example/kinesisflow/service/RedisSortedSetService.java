package org.example.kinesisflow.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RedisSortedSetService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSortedSetService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addElement(String key, String value, Double score) {
        Boolean result =   redisTemplate.opsForZSet().add(key, value, score);
        System.out.println("....................................................................");
        System.out.println(result);
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    public Set<String> getElementsInRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<String> getElementsWithScores(String key, double minScore, double maxScore) {
        return redisTemplate.opsForZSet().rangeByScore(key, minScore, maxScore);
    }

    public void removeElement(String key, String value) {
        redisTemplate.opsForZSet().remove(key, value);
    }

    public Set<String> getAllElements(String key) {
        return redisTemplate.opsForZSet().range(key, 0, -1);
    }

    public void deleteAll() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public Double getScore(String key, String value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    public Set<String> getAllKeys() {
        return redisTemplate.keys("*");
    }
}
