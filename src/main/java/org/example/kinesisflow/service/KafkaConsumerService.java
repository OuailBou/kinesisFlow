package org.example.kinesisflow.service;

import org.example.kinesisflow.record.cryptoEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @KafkaListener(id = "kinesis-listener", topics = "data-injest", groupId = "kinesis-group")
    public void listen(cryptoEvent in) {
        System.out.println("Mensaje recibido: "+in);
    }
}
