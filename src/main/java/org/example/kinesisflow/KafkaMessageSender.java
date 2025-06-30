package org.example.kinesisflow;

import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessageSender implements CommandLineRunner {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaMessageSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run(String... args) {
        String topic = "data-injest";
        String mensaje = "¡Hola desde Spring Boot!";
        kafkaTemplate.send(topic, mensaje);
        System.out.println("Mensaje enviado a Kafka: " + mensaje);
    }
}
