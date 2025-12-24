package com.kinesisflow.service;

import com.kinesisflow.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class CleanupService {

    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);

    private final AlertRepository alertRepository;
    private final RedisTemplate<String, String> redisTemplateString;
    private final RedisTemplate<String, Double> redisTemplateDouble;

    public CleanupService(AlertRepository alertRepository,
                          RedisTemplate<String, String> redisTemplateString,
                          RedisTemplate<String, Double> redisTemplateDouble) {
        this.alertRepository = alertRepository;
        this.redisTemplateString = redisTemplateString;
        this.redisTemplateDouble = redisTemplateDouble;
    }

    @Transactional
    public void clearDatabaseAndCache() {
        logger.info("Deleting all alerts from database...");
        alertRepository.deleteAll();

        logger.info("Deleting all keys from Redis (String template)...");
        deleteAllKeysFromTemplate(redisTemplateString);

        logger.info("Deleting all keys from Redis (Double template)...");
        deleteAllKeysFromTemplate(redisTemplateDouble);
    }

    private void deleteAllKeysFromTemplate(RedisTemplate<String, ?> redisTemplate) {
        Set<String> keys = new HashSet<>();
        try (var cursor = redisTemplate.scan(ScanOptions.scanOptions().match("*").count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception e) {
            logger.error("Error scanning Redis keys", e);
        }

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            logger.info("Deleted {} keys from Redis", keys.size());
        } else {
            logger.info("No Redis keys found to delete");
        }
    }
}
