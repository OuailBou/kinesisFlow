package org.example.kinesisflow.controller;

import jakarta.validation.Valid;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }


    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeToAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        AlertDTO alert = alertService.createOrUpdateAlertSubscription(alertDTO, authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Alert created");
        response.put("username", authentication.getName());
        response.put("alert", alert);


        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribeFromAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        alertService.unsubscribeFromAlert(alertDTO, authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "You have unsubscribed from alert succesfully");
        response.put("username", authentication.getName());
        response.put("alert", alertDTO);
        return ResponseEntity.ok(response);
    }
}

