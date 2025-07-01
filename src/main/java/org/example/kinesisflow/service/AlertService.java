package org.example.kinesisflow.service;

import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public List<Alert> findAll() {
        return alertRepository.findAll();
    }

    public Optional<Alert> findById(Long id) {
        return alertRepository.findById(id);
    }

    public Alert save(Alert alert) {
        return alertRepository.save(alert);
    }

    public void deleteById(Long id) {
        alertRepository.deleteById(id);
    }
}
