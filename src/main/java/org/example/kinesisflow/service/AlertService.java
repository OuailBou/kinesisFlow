package org.example.kinesisflow.service;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public Optional<Alert> findById(Long id) {
        return alertRepository.findById(id);
    }

    public Optional<Alert> findByUser(User u) {
        return alertRepository.findByUser(u);
    }


    public Alert save(Alert alert) {
        return alertRepository.save(alert);
    }

    public void deleteById(Long id) {
        alertRepository.deleteById(id);
    }

    public boolean isOwnedByUser(Alert alert, User user) {
        return alert.getUser() != null && alert.getUser().getId().equals(user.getId());
    }



}
