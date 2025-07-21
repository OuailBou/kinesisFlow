package org.example.kinesisflow.controller;
import jakarta.validation.Valid;
import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.service.KafkaProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {


    private final KafkaProducerService producerService;

    public IngestController(KafkaProducerService producerService) {
        this.producerService = producerService;
    }



    @PostMapping("/ingest")
    public ResponseEntity<String> sendMessage(@RequestBody cryptoEvent event) {
        producerService.send(event);
        return ResponseEntity.ok("Mensaje enviado a Kafka");
    }

}