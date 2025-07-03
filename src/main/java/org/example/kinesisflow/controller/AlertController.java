package org.example.kinesisflow.controller;

import jakarta.validation.Valid;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.mapper.AlertMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.AlertService;
import org.example.kinesisflow.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    public AlertController(AlertService alertService, UserService userService) {
        this.alertService = alertService;
        this.userService = userService;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        String username = authentication.getName();
        return userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping
    public List<AlertDTO> getAllAlerts(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        return alertService.findByUser(user).stream()
                .map(AlertMapper::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDTO createAlert(@RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        Alert alert = AlertMapper.fromDTO(alertDTO);
        alert.setUser(user);

        Alert saved = alertService.save(alert);
        return AlertMapper.toDTO(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertDTO> updateAlert(@PathVariable Long id, @RequestBody @Valid AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        return alertService.findById(id)
                .filter(alert -> alertService.isOwnedByUser(alert, user))
                .map(alert -> {
                    alert.setAsset(alertDTO.getAsset());
                    alert.setPrice(alertDTO.getPrice());
                    alert.setComparisonType(alertDTO.getComparisonType());
                    Alert updated = alertService.save(alert);
                    return ResponseEntity.ok(AlertMapper.toDTO(updated));
                })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAlert(@PathVariable Long id, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        return alertService.findById(id)
                .filter(alert -> alertService.isOwnedByUser(alert, user))
                .map(alert -> {
                    alertService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDTO> getAlertById(@PathVariable Long id, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        return alertService.findById(id)
                .filter(alert -> alertService.isOwnedByUser(alert, user))
                .map(AlertMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }
}