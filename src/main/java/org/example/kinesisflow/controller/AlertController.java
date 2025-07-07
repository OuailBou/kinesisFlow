package org.example.kinesisflow.controller;

import jakarta.validation.Valid;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }


    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDTO subscribeToAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        return alertService.createOrUpdateAlertSubscription(alertDTO, authentication);
    }

    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribeFromAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        alertService.unsubscribeFromAlert(alertDTO, authentication);
        return ResponseEntity.noContent().build();
    }
}

