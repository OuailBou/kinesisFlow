package com.kinesisflow.controller;

import jakarta.validation.Valid;
import com.kinesisflow.record.CryptoEvent;
import com.kinesisflow.service.KafkaProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@Tag(name = "Ingestion", description = "Send crypto events to Kafka")
public class IngestController {

    private final KafkaProducerService producerService;

    public IngestController(KafkaProducerService producerService) {
        this.producerService = producerService;
    }

    @Operation(summary = "Send event to Kafka", description = "Sends a validated CryptoEvent to the Kafka topic.")
    @ApiResponse(responseCode = "200", description = "Message sent successfully")
    @PostMapping("/ingest")
    public ResponseEntity<String> sendMessage(@RequestBody @Valid CryptoEvent event) {
        producerService.send(event);
        return ResponseEntity.ok("Message sent to Kafka");
    }
}
