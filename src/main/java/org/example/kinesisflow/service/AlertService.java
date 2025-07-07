package org.example.kinesisflow.service;

import jakarta.persistence.OptimisticLockException;
import org.example.kinesisflow.event.AlertCreatedEvent;
import org.example.kinesisflow.event.AlertDeletedEvent;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.mapper.AlertMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.AlertId;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public AlertService(AlertRepository alertRepository,
                        UserService userService,
                        ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Alert> findById(AlertId id) {
        return alertRepository.findById(id);
    }

    public boolean isOwnedByUser(Alert alert, User user) {
        return alert.getUsers() != null && alert.getUsers().stream().anyMatch(u -> u.getId().equals(user.getId()));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public AlertDTO createOrUpdateAlertSubscription(AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        AlertId alertId = AlertMapper.toId(alertDTO);
        Alert alert = findOrCreateAlert(alertId, alertDTO);

        alert.addUser(user);

        AlertDTO dto = AlertMapper.toDTO(alert);

        if (alert.getUsers().size() == 1) {
            eventPublisher.publishEvent(new AlertCreatedEvent(alert));
        }

        return dto;
    }

    private Alert findOrCreateAlert(AlertId alertId, AlertDTO alertDTO) {
        return alertRepository.findById(alertId).orElseGet(() -> {
            try {
                Alert newAlert = AlertMapper.fromDTO(alertDTO);
                alertRepository.save(newAlert);
                return newAlert;
            } catch (DataIntegrityViolationException e) {
                return this.findById(alertId)
                        .orElseThrow(() -> new IllegalStateException("Race condition recovery failed: Alert should exist but was not found."));
            }
        });
    }

    @Transactional
    @Retryable(
            value = OptimisticLockException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public void unsubscribeFromAlert(AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        AlertId alertId = AlertMapper.toId(alertDTO);

        Alert alert = this.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));

        if (isOwnedByUser(alert, user)) {
            alert.removeUser(user);
            if (alert.getUsers().isEmpty()) {
                alertRepository.delete(alert);
                eventPublisher.publishEvent(new AlertDeletedEvent(alert));
            }
        }
    }

    @Recover
    public void recoverDeleteAlertSubscription(OptimisticLockException e, AlertDTO alertDTO, Authentication authentication) {
        throw new RuntimeException("Concurrent conflict detected after multiple retries. Please try again.", e);
    }
}
