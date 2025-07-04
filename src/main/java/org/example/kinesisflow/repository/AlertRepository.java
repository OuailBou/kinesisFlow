package org.example.kinesisflow.repository;

import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUser(User u);
}
