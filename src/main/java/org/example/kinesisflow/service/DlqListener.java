package org.example.kinesisflow.listener;

import lombok.Data;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.kinesisflow.record.cryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Component
public class DlqListener {

    private static final Logger log = LoggerFactory.getLogger(DlqListener.class);

    // Un getter para que el test pueda acceder a los registros
    // Guardaremos el registro completo del consumidor, que tiene más información
    private final List<ConsumerRecord<String, String>> dlqRecords = new CopyOnWriteArrayList<>();

    // 1. Le decimos al listener que espere un ConsumerRecord<String, String>
    // Esto fuerza a Spring Kafka a usar los deserializadores de String simples
    // para este listener específico, sin importar la configuración global.
    @KafkaListener(
            id = "dlq-listener",
            topics = "raw-market-data-DLQ", // El nombre del topic suele ser .DLT por convención de Spring
            groupId = "dlq-processor-group"
    )
    public void listenDlq(ConsumerRecord<String, String> record) {
        log.warn("DLQ MESSAGE RECEIVED: Key='{}', Payload='{}', Headers='{}'",
                record.key(), record.value(), record.headers());

        // 2. Guardamos el registro completo, no solo el payload
        dlqRecords.add(record);
    }

    public void clearMessages() {
        dlqRecords.clear();
    }

}