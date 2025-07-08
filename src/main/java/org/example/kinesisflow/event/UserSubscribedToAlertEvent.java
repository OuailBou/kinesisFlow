package org.example.kinesisflow.event;

import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;

public record UserSubscribedToAlertEvent(Alert alert, User user) {


}

