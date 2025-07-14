package org.example.kinesisflow.service;

import org.example.kinesisflow.mapper.EventToNotificationMapper;
import org.example.kinesisflow.record.cryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final RedisStringService redisStringService;
    private final RedisSortedSetService redisSortedSetService;
    private final RedisMessagePublisher redisMessagePublisher;

    public KafkaConsumerService(RedisStringService redisStringService,
                                RedisSortedSetService redisSortedSetService, RedisMessagePublisher redisMessagePublisher
    )  {
        this.redisStringService = redisStringService;
        this.redisSortedSetService = redisSortedSetService;
        this.redisMessagePublisher = redisMessagePublisher;
    }

    @KafkaListener(
            id = "kinesis-listener",
            topics = "raw-market-data",
            groupId = "kinesis-group",
            concurrency = "3"
    )
    public void listen(cryptoEvent cryptoEvent) {
        log.info("Message received: {}", cryptoEvent);

        try {
            processCryptoEvent(cryptoEvent);
        } catch (Exception e) {
            log.error("Error processing crypto event: {}", cryptoEvent, e);
        }
    }

    private void processCryptoEvent(cryptoEvent cryptoEvent) {
        Optional<BigDecimal> formerPrice = getPreviousPrice(cryptoEvent.asset());

        if (formerPrice.isEmpty()) {
            savePriceAndReturn(cryptoEvent);
            return;
        }

        List<String> affectedUsers = getAffectedUsers(cryptoEvent, formerPrice.get());

        if (!affectedUsers.isEmpty()) {
            log.info("Found {} affected users for asset {} price change from {} to {}",
                    affectedUsers.size(), cryptoEvent.asset(), formerPrice.get(), cryptoEvent.price());

            processAffectedUsers(affectedUsers, cryptoEvent);
        }

        updateCurrentPrice(cryptoEvent);
    }

    private Optional<BigDecimal> getPreviousPrice(String asset) {
        Double price = redisStringService.get(asset);
        return price != null ? Optional.of(BigDecimal.valueOf(price)) : Optional.empty();
    }

    private void savePriceAndReturn(cryptoEvent cryptoEvent) {
        redisStringService.save(cryptoEvent.asset(), cryptoEvent.price());
        log.debug("Saved initial price for asset: {}", cryptoEvent.asset());
    }

    private List<String> getAffectedUsers(cryptoEvent cryptoEvent, BigDecimal formerPrice) {
        PriceComparison comparison = comparePrices(cryptoEvent.price(), formerPrice);

        return switch (comparison) {
            case HIGHER -> getUsersForPriceIncrease(cryptoEvent.asset(), formerPrice, cryptoEvent.price());
            case LOWER -> getUsersForPriceDecrease(cryptoEvent.asset(), cryptoEvent.price(), formerPrice);
            case EQUAL -> Collections.emptyList();
        };
    }

    private PriceComparison comparePrices(BigDecimal currentPrice, BigDecimal formerPrice) {
        int comparison = currentPrice.compareTo(formerPrice);
        if (comparison > 0) return PriceComparison.HIGHER;
        if (comparison < 0) return PriceComparison.LOWER;
        return PriceComparison.EQUAL;
    }

    private List<String> getUsersForPriceIncrease(String asset, BigDecimal formerPrice, BigDecimal currentPrice) {
        String gtKey = redisSortedSetService.createRuleIndexKey(asset, "1");
        Set<String> values = redisSortedSetService.getRangeByScore(
                gtKey, formerPrice, currentPrice, true, false
        );
        return extractUserIds(values);
    }

    private List<String> getUsersForPriceDecrease(String asset, BigDecimal currentPrice, BigDecimal formerPrice) {
        String ltKey = redisSortedSetService.createRuleIndexKey(asset, "-1");
        Set<String> values = redisSortedSetService.getRangeByScore(
                ltKey, currentPrice, formerPrice, true, false
        );
        return extractUserIds(values);
    }

    private List<String> extractUserIds(Set<String> values) {
        return values.stream()
                .map(this::extractUserId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<String> extractUserId(String value) {
        if (value == null || !value.contains(":")) {
            log.warn("Invalid value format: {}", value);
            return Optional.empty();
        }
        return Optional.of(value.split(":")[0]);
    }

    private void processAffectedUsers(List<String> users, cryptoEvent cryptoEvent) {
        log.info(".......................................................Processing {} affected user", users.getFirst());

        users.forEach(u -> redisMessagePublisher.publish("alerts", EventToNotificationMapper.mapToNotification(cryptoEvent, u)));

        log.info("Processing {} affected users for asset {}", users.size(), cryptoEvent.asset());
    }



    private void updateCurrentPrice(cryptoEvent cryptoEvent) {
        redisStringService.save(cryptoEvent.asset(), cryptoEvent.price());
        log.debug("Updated current price for asset: {}", cryptoEvent.asset());
    }

    private enum PriceComparison {
        HIGHER, LOWER, EQUAL
    }
}