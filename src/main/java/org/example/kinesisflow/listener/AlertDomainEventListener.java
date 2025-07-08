package org.example.kinesisflow.listener;

import org.example.kinesisflow.event.AlertCreatedEvent;
import org.example.kinesisflow.event.AlertDeletedEvent;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertDomainEventListener {

    private final RedisSortedSetService redisSortedSetService;

    public AlertDomainEventListener(RedisSortedSetService redisSortedSetService) {
        this.redisSortedSetService = redisSortedSetService;
    }

    // FOR TESTING I USE EVENT LISTENER
    @EventListener
    //@TransactionalEventListener
    public void handleAlertCreated(AlertCreatedEvent event) {
        var alert = event.getAlert();
        String key = alert.getId().getAsset() + ":" + alert.getId().getComparisonType();
        String value = key + ":" + alert.getId().getPrice();
        Double score = alert.getId().getPrice();

        redisSortedSetService.addElement(key, value, score);
    }

    @EventListener
    //@TransactionalEventListener
    public void handleAlertDeleted(AlertDeletedEvent event) {
        var alert = event.getAlert();
        String key = alert.getId().getAsset() + ":" + alert.getId().getComparisonType();

        redisSortedSetService.deleteKey(key);
    }
}
