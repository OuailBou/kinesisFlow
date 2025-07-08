package org.example.kinesisflow.event;

import lombok.Data;
import org.example.kinesisflow.model.Alert;

@Data
public class AlertCreatedEvent {

    private final Alert alert;

    public AlertCreatedEvent(Alert alert) {
        this.alert = alert;
    }


}

