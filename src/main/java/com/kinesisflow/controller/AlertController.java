package com.kinesisflow.controller;

import jakarta.validation.Valid;
import com.kinesisflow.dto.AlertDTO;
import com.kinesisflow.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Operations for subscribing/unsubscribing to alerts")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(
            summary = "Subscribe to an alert",
            description = "Creates or updates an alert subscription for the authenticated user. Requires a JWT token obtained from /auth/login.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Alert subscription created"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized – JWT required")
            }
    )
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeToAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        AlertDTO alert = alertService.createOrUpdateAlertSubscription(alertDTO, authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Alert created");
        response.put("username", authentication.getName());
        response.put("alert", alert);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Unsubscribe from an alert",
            description = "Removes an alert subscription for the authenticated user. Requires a JWT token obtained from /auth/login.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully unsubscribed from alert"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized – JWT required")
            }
    )
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribeFromAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        alertService.unsubscribeFromAlert(alertDTO, authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "You have unsubscribed from alert successfully");
        response.put("username", authentication.getName());
        response.put("alert", alertDTO);
        return ResponseEntity.ok(response);
    }
}
