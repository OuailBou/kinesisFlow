package org.example.kinesisflow.listener;

import jakarta.persistence.OptimisticLockException;
import org.example.kinesisflow.event.UserSubscribedToAlertEvent;
import org.example.kinesisflow.event.UserUnsubscribedFromAlertEvent;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Profile("!test")
@Component
public class AlertDomainEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AlertDomainEventListener.class);

    private final RedisSortedSetService redisSortedSetService;

    public AlertDomainEventListener(RedisSortedSetService redisSortedSetService) {
        this.redisSortedSetService = redisSortedSetService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleUserSubscribed(UserSubscribedToAlertEvent event) {
        logger.info("Sync subscription to Redis for user: {}, alertId: {}",
                event.user().getUsername(), event.alert().getId());

        Alert alert = event.alert();
        User user = event.user();

        String key = redisSortedSetService.createRuleIndexKey(alert.getId().getAsset(),
                String.valueOf(alert.getId().getComparisonType()));
        String value = redisSortedSetService.createRuleIndexValue(user, alert.getId().getPrice());

        logger.debug("Adding element to Redis sorted set. key='{}', value='{}', score={}",
                key, value, alert.getId().getPrice());

        redisSortedSetService.addElement(key, value, alert.getId().getPrice());

        logger.info("Subscription synced successfully for user: {}", user.getUsername());
    }

    @Recover
    public void recoverSubscriptionSync(DataAccessException e, UserSubscribedToAlertEvent event) {
        logger.error("RECOVER: Failed to sync subscription for user {} after retries. Exception: {}",
                event.user().getUsername(), e.getMessage());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleUserUnsubscribed(UserUnsubscribedFromAlertEvent event) {
        logger.info("Sync unsubscription to Redis for user: {}, alertId: {}",
                event.user().getUsername(), event.alert().getId());

        Alert alert = event.alert();
        User user = event.user();

        String key = redisSortedSetService.createRuleIndexKey(alert.getId().getAsset(),
                String.valueOf(alert.getId().getComparisonType()));
        String value = redisSortedSetService.createRuleIndexValue(user, alert.getId().getPrice());

        logger.debug("Removing element from Redis sorted set. key='{}', value='{}'",
                key, value);

        redisSortedSetService.removeElement(key, value);

        logger.info("Unsubscription synced successfully for user: {}", user.getUsername());
    }

    @Recover
    public void recoverUnsubscriptionSync(DataAccessException e, UserUnsubscribedFromAlertEvent event) {
        logger.error("RECOVER: Failed to sync unsubscription for user {} after retries. Exception: {}",
                event.user().getUsername(), e.getMessage());
    }
}
