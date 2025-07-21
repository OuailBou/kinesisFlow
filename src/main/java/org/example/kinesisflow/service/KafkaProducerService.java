package org.example.kinesisflow.service;
import org.example.kinesisflow.record.CryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;


@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, CryptoEvent> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);


    public KafkaProducerService(KafkaTemplate<String, CryptoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    public void send(CryptoEvent event) {

        String topic =  "raw-market-data";

        CompletableFuture<SendResult<String, CryptoEvent>> future = kafkaTemplate.send(topic, event.asset(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Asynchronous failure while sending message: {}", ex.getMessage());
            } else {
                log.trace("Asynchronous success. Offset: {}", result.getRecordMetadata().offset());
            }
        });


    }
}
