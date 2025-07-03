package org.example.kinesisflow.service;

import jakarta.transaction.Transactional;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private AlertRepository alertRepository;

    @KafkaListener(id = "kinesis-listener", topics = "data-injest", groupId = "kinesis-group")
    public void listen(cryptoEvent in) {
        log.info("message received {}", in);
        //ONLY FOR TESTING
        this.alertRepository.save(new Alert(new BigDecimal(500), "SOL", -1));
        this.alertRepository.save(new Alert(new BigDecimal(500), "SOL", 1));

    }
}
