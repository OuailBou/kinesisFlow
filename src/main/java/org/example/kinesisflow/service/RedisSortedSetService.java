package org.example.kinesisflow.service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RedisSortedSetService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSortedSetService (RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }



    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addElement(String key, String value, Double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }
    // Retrieve elements in ascending order
    public Set<String> getElementsInRange(String key,long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    // Retrieve elements with scores
    public Set<String> getElementsWithScores(String key, double minScore, double maxScore) {
        return redisTemplate.opsForZSet().rangeByScore(key, minScore, maxScore);
    }

    // Remove an element
    public void removeElement(String key, String value) {
        redisTemplate.opsForZSet().remove(key, value);
    }
}
