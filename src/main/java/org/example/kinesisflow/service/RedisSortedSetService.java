package org.example.kinesisflow.service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RedisSortedSetService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSortedSetService (RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    private static final String SORTED_SET_KEY = "mySortedSet";

    // Add elements to the sorted set
    public void addElement(String value, double score) {
        redisTemplate.opsForZSet().add(SORTED_SET_KEY, value, score);
    }

    // Retrieve elements in ascending order
    public Set<String> getElementsInRange(long start, long end) {
        return redisTemplate.opsForZSet().range(SORTED_SET_KEY, start, end);
    }

    // Retrieve elements with scores
    public Set<String> getElementsWithScores(double minScore, double maxScore) {
        return redisTemplate.opsForZSet().rangeByScore(SORTED_SET_KEY, minScore, maxScore);
    }

    // Remove an element
    public void removeElement(String value) {
        redisTemplate.opsForZSet().remove(SORTED_SET_KEY, value);
    }
}
