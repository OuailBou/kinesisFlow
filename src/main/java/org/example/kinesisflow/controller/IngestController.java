package org.example.kinesisflow.controller;
import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {

    @Autowired
    private KafkaProducerService producerService;



    @PostMapping("/ingest")
    public ResponseEntity<String> sendMessage(@RequestBody cryptoEvent event) {
        producerService.send(event);
        return ResponseEntity.ok("Mensaje enviado a Kafka");
    }

}
