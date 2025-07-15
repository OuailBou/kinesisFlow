package org.example.kinesisflow.listener;

import org.example.kinesisflow.event.UserSubscribedToAlertEvent;
import org.example.kinesisflow.event.UserUnsubscribedFromAlertEvent;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.example.kinesisflow.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertDomainEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AlertDomainEventListener.class);

    private final RedisSortedSetService redisSortedSetService;

    public AlertDomainEventListener(RedisSortedSetService redisSortedSetService) {
        this.redisSortedSetService = redisSortedSetService;
    }

    //@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @EventListener
    public void handleAlertCreated(UserSubscribedToAlertEvent event) {
        logger.info("Received UserSubscribedToAlertEvent for alertId={} and user={}",
                event.alert().getId(), event.user().getUsername());
        processAlert(event.alert(), event.user(), true);
    }

    //@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @EventListener
    public void handleAlertDeleted(UserUnsubscribedFromAlertEvent event) {
        logger.info("Received UserUnsubscribedFromAlertEvent for alertId={} and user={}",
                event.alert().getId(), event.user().getUsername());
        processAlert(event.alert(), event.user(), false);
    }

    private void processAlert(Alert alert, User user, boolean isCreation) {
        String key = redisSortedSetService.createRuleIndexKey(
                alert.getId().getAsset(),
                String.valueOf(alert.getId().getComparisonType())
        );

        String value = redisSortedSetService.createRuleIndexValue(
                user,
                alert.getId().getPrice()
        );

        try {
            if (isCreation) {
                logger.debug("Adding element to Redis sorted set. key='{}', value='{}', score={}",
                        key, value, alert.getId().getPrice());
                redisSortedSetService.addElement(key, value, alert.getId().getPrice());
                logger.info("Added element to Redis sorted set successfully.");
            } else {
                logger.debug("Removing element from Redis sorted set. key='{}', value='{}'",
                        key, value);
                redisSortedSetService.removeElement(key, value);
                logger.info("Removed element from Redis sorted set successfully.");
            }
        } catch (Exception e) {
            logger.error("Error processing Redis sorted set operation for alertId={}, user={}, isCreation={}",
                    alert.getId(), user.getUsername(), isCreation, e);
        }
    }
}
