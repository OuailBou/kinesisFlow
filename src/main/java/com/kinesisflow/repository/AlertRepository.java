package com.kinesisflow.repository;

import com.kinesisflow.model.Alert;
import com.kinesisflow.model.AlertId;
import com.kinesisflow.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findById(AlertId id);
}
