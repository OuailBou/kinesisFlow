package com.kinesisflow.event;
import com.kinesisflow.model.Alert;
import com.kinesisflow.model.User;

public record UserUnsubscribedFromAlertEvent(Alert alert, User user) {

}
