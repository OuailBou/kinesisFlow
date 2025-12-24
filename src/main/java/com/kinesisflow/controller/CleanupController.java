package com.kinesisflow.controller;

import com.kinesisflow.service.CleanupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cleanup")
public class CleanupController {

    private final CleanupService cleanupService;

    public CleanupController(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @DeleteMapping
    public ResponseEntity<String> clearAllData() {
        cleanupService.clearDatabaseAndCache();
        return ResponseEntity.ok("All alerts in DB and all Redis cache cleared successfully");
    }
}
