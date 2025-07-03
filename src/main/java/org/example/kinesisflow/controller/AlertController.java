package org.example.kinesisflow.controller;

import jakarta.validation.Valid;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.mapper.AlertMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertDTO> getAllAlerts() {
        return alertService.findAll().stream()
                .map(AlertMapper::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDTO> getAlertById(@PathVariable Long id) {
        return alertService.findById(id)
                .map(AlertMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDTO createAlert(@RequestBody @Valid AlertDTO alertDTO) {
        Alert alert = AlertMapper.fromDTO(alertDTO);
        Alert saved = alertService.save(alert);
        return AlertMapper.toDTO(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertDTO> updateAlert(@PathVariable Long id, @RequestBody @Valid AlertDTO alertDTO) {
        return alertService.findById(id)
                .map(existingAlert -> {
                    existingAlert.setAsset(alertDTO.getAsset());
                    existingAlert.setPrice(alertDTO.getPrice());
                    existingAlert.setComparisonType(alertDTO.getComparisonType());
                    Alert updated = alertService.save(existingAlert);
                    return ResponseEntity.ok(AlertMapper.toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        if (alertService.findById(id).isPresent()) {
            alertService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
