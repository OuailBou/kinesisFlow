package org.example.kinesisflow.event;

import lombok.Data;
import org.example.kinesisflow.model.Alert;

@Data
public class AlertDeletedEvent {

    private final Alert alert;

    public AlertDeletedEvent(Alert alert) {
        this.alert = alert;
    }
}
