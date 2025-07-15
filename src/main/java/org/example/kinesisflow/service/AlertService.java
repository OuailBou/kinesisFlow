package org.example.kinesisflow.service;

import jakarta.persistence.OptimisticLockException;
import org.example.kinesisflow.event.UserSubscribedToAlertEvent;
import org.example.kinesisflow.event.UserUnsubscribedFromAlertEvent;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.exception.AlertNotFoundException;
import org.example.kinesisflow.exception.ConcurrentConflictException;
import org.example.kinesisflow.exception.UserNotFoundException;
import org.example.kinesisflow.mapper.AlertMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.AlertId;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

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
        logger.debug("Finding alert by id: {}", id);
        return alertRepository.findById(id);
    }

    public boolean isOwnedByUser(Alert alert, User user) {
        boolean owned = alert.getUsers() != null && alert.getUsers().stream().anyMatch(u -> u.getId().equals(user.getId()));
        logger.debug("Checking ownership of alert {} by user {}: {}", alert.getId(), user.getId(), owned);
        return owned;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        logger.debug("Getting authenticated user from authentication: {}", authentication.getName());
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> {
                    logger.error("User not found with username: {}", authentication.getName());
                    return new UserNotFoundException("User not found with username: " + authentication.getName());
                });
    }

    @Transactional
    public AlertDTO createOrUpdateAlertSubscription(AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        AlertId alertId = AlertMapper.toId(alertDTO);
        logger.info("User {} is subscribing alert {}", user.getUsername(), alertId);

        Alert alert = alertRepository.findById(alertId).orElseGet(() -> {
            try {
                logger.debug("Alert not found. Creating new alert with id: {}", alertId);
                Alert newAlert = AlertMapper.fromDTO(alertDTO);
                return alertRepository.save(newAlert);
            } catch (DataIntegrityViolationException e) {
                logger.warn("Data integrity violation when creating alert {}: {}", alertId, e.getMessage());
                return alertRepository.findById(alertId).orElseThrow(() -> new ConcurrentConflictException("Concurrent conflict detected after multiple retries. Please try again.", e));
            }
        });

        boolean added = alert.addUser(user);
        if (added) {
            logger.info("User {} added to alert {}", user.getUsername(), alertId);
            alertRepository.save(alert);
            eventPublisher.publishEvent(new UserSubscribedToAlertEvent(alert, user));
        } else {
            logger.info("User {} was already subscribed to alert {}", user.getUsername(), alertId);
        }

        return AlertMapper.toDTO(alert);
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockException.class, backoff = @Backoff(delay = 100))
    public void unsubscribeFromAlert(AlertDTO alertDTO, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        AlertId alertId = AlertMapper.toId(alertDTO);
        logger.info("User {} is unsubscribing from alert {}", user.getUsername(), alertId);

        Alert alert = this.findById(alertId)
                .orElseThrow(() -> {
                    logger.error("Alert not found with id: {}", alertId);
                    return new AlertNotFoundException("Alert not found with id: " + alertId);
                });

        if (isOwnedByUser(alert, user)) {
            alert.removeUser(user);
            logger.info("User {} removed from alert {}", user.getUsername(), alertId);

            if (alert.getUsers().isEmpty()) {
                logger.info("No users left subscribed to alert {}. Deleting alert.", alertId);
                alertRepository.delete(alert);
            }
            eventPublisher.publishEvent(new UserUnsubscribedFromAlertEvent(alert, user));
        } else {
            logger.warn("User {} tried to unsubscribe from alert {} but was not subscribed.", user.getUsername(), alertId);
        }
    }

    @Recover
    public void recoverDeleteAlertSubscription(OptimisticLockException e, AlertDTO alertDTO, Authentication authentication) {
        logger.error("Recovery after optimistic lock failure for alert {} by user {}: {}", AlertMapper.toId(alertDTO), authentication.getName(), e.getMessage());
        throw new ConcurrentConflictException("Concurrent conflict detected after multiple retries. Please try again.", e);
    }
}
