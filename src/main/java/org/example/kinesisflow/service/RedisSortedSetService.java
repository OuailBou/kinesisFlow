package org.example.kinesisflow.service;

import org.example.kinesisflow.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class RedisSortedSetService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSortedSetService.class);

    private final RedisTemplate<String, String> redisTemplateString;

    public RedisSortedSetService(RedisTemplate<String, String> redisTemplateString) {
        this.redisTemplateString = redisTemplateString;
    }

    public String createRuleIndexKey(String asset, String type) {
        return String.join(":", asset, type);
    }

    public String createRuleIndexValue(User user, BigDecimal price) {
        return String.join(":", user.getUsername(), price.stripTrailingZeros().toPlainString());
    }

    public void addElement(String key, String value, BigDecimal score) {
        Boolean result = redisTemplateString.opsForZSet().add(key, value, score.doubleValue());
        if (Boolean.FALSE.equals(result)) {
            logger.warn("Failed to add element to Redis ZSet: key={}, value={}, score={}", key, value, score);
        } else {
            logger.debug("Added element to Redis ZSet: key={}, value={}, score={}", key, value, score);
        }
    }

    public void removeElement(String key, String value) {
        Long removed = redisTemplateString.opsForZSet().remove(key, value);
        if (removed != null && removed == 0) {
            logger.warn("Element not found for removal: key={}, value={}", key, value);
        } else {
            logger.debug("Removed element from Redis ZSet: key={}, value={}", key, value);
        }
    }

    public Set<String> getRangeByScore(String key, BigDecimal minScore, BigDecimal maxScore, boolean minInclusive, boolean maxInclusive) {
        double adjustedMin = minInclusive ? minScore.doubleValue() : Math.nextUp(minScore.doubleValue());
        double adjustedMax = maxInclusive ? maxScore.doubleValue() : Math.nextDown(maxScore.doubleValue());

        return redisTemplateString.opsForZSet().rangeByScore(key, adjustedMin, adjustedMax);
    }

    public Set<String> getAllElements(String key) {
        return redisTemplateString.opsForZSet().range(key, 0, -1);
    }

    public Double getScore(String key, String value) {
        return redisTemplateString.opsForZSet().score(key, value);
    }

    public Set<String> getAllKeys() {
        return redisTemplateString.keys("*");
    }

    public void deleteAll() {
        Set<String> keys = redisTemplateString.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplateString.delete(keys);
            logger.info("Deleted all Redis keys: {}", keys);
        } else {
            logger.info("No Redis keys to delete");
        }
    }
}
