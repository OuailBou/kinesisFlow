package org.example.kinesisflow.service;

import org.example.kinesisflow.model.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.util.Set;

@Service
public class RedisSortedSetService {

    private final RedisTemplate<String, String> redisTemplateDouble;

    public RedisSortedSetService(RedisTemplate<String, String> redisTemplateString) {
        this.redisTemplateDouble = redisTemplateString;
    }

    public String createRuleIndexKey(String asset, String type) {
        return String.join(":", asset, type);
    }
    public String createRuleIndexValue(User user, BigDecimal price) {
        return String.join(":", user.getId().toString(), price.toString());
    }

    public void addElement(String key, String value, BigDecimal score) {
        Boolean result =   redisTemplateDouble.opsForZSet().add(key, value, score.doubleValue());
        System.out.println("....................................................................");
        System.out.println(result);
    }

    public void deleteKey(String key) {
        redisTemplateDouble.delete(key);
    }

    public Set<String> getElementsInRange(String key, long start, long end) {
        return redisTemplateDouble.opsForZSet().range(key, start, end);
    }

    public Set<String> getRangeByScore(String key, BigDecimal minScore, BigDecimal maxScore, boolean minInclusive, boolean maxInclusive) {
        double adjustedMin = minInclusive ? minScore.doubleValue() : Math.nextUp(minScore.doubleValue());
        double adjustedMax = maxInclusive ? maxScore.doubleValue() : Math.nextDown(maxScore.doubleValue());

        return redisTemplateDouble.opsForZSet().rangeByScore(key, adjustedMin, adjustedMax);
    }



    public void removeElement(String key, String value) {
        redisTemplateDouble.opsForZSet().remove(key, value);
    }

    public Set<String> getAllElements(String key) {
        return redisTemplateDouble.opsForZSet().range(key, 0, -1);
    }

    public void deleteAll() {
        Set<String> keys = redisTemplateDouble.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplateDouble.delete(keys);
        }
    }

    public Double getScore(String key, String value) {
        return redisTemplateDouble.opsForZSet().score(key, value);
    }

    public Set<String> getAllKeys() {
        return redisTemplateDouble.keys("*");
    }
}
