package org.example.kinesisflow.listener;

import org.example.kinesisflow.event.AlertCreatedEvent;
import org.example.kinesisflow.event.AlertDeletedEvent;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AlertDomainEventListener {

    private final RedisSortedSetService redisSortedSetService;

    public AlertDomainEventListener(RedisSortedSetService redisSortedSetService) {
        this.redisSortedSetService = redisSortedSetService;
    }

    @TransactionalEventListener
    public void handleAlertCreated(AlertCreatedEvent event) {
        var alert = event.getAlert();
        String key = alert.getId().getAsset() + ":" + alert.getId().getComparisonType();
        String value = key + ":" + alert.getId().getPrice();
        Double score = alert.getId().getPrice();

        redisSortedSetService.addElement(key, value, score);
    }

    @TransactionalEventListener
    public void handleAlertDeleted(AlertDeletedEvent event) {
        var alert = event.getAlert();
        String key = alert.getId().getAsset() + ":" + alert.getId().getComparisonType();

        redisSortedSetService.deleteKey(key);
    }
}
