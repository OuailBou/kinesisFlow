package org.example.kinesisflow.service;
import org.example.kinesisflow.record.cryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, cryptoEvent> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);


    public KafkaProducerService(KafkaTemplate<String, cryptoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    public void send(cryptoEvent event) {

        String topic =  "data-injest";
        kafkaTemplate.send(topic, event);
        log.info("message sent to topic {}", topic);




    }
}
