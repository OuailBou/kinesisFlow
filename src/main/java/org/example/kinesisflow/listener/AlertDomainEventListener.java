package org.example.kinesisflow.listener;

import org.example.kinesisflow.event.UserSubscribedToAlertEvent;
import org.example.kinesisflow.event.UserUnsubscribedFromAlertEvent;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.example.kinesisflow.model.Alert;
import org.springframework.context.event.EventListener;
// import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertDomainEventListener {

    private final RedisSortedSetService redisSortedSetService;

    public AlertDomainEventListener(RedisSortedSetService redisSortedSetService) {
        this.redisSortedSetService = redisSortedSetService;
    }

    @EventListener
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAlertCreated(UserSubscribedToAlertEvent event) {
        processAlert(event.alert(), event.user(), true);
    }

    @EventListener
    //@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAlertDeleted(UserUnsubscribedFromAlertEvent event) {
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

        if (isCreation) {
            redisSortedSetService.addElement(key, value, alert.getId().getPrice());
        } else {
            redisSortedSetService.removeElement(key, value);
        }
    }
}
