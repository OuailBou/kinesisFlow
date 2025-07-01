package org.example.kinesisflow.service;

import org.example.kinesisflow.record.cryptoEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, cryptoEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, cryptoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }



    @KafkaListener(id = "kinesis-listener", topics = "data-injest", groupId = "kinesis-group")
    public void listen(cryptoEvent in) {
        System.out.println("Mensaje recibido: "+in);
    }

    public void send(cryptoEvent event) {
        String topic =  "data-injest";
        kafkaTemplate.send(topic, event);


        System.out.println("Mensaje recibido en send y enviado a Kafka");


    }
}
