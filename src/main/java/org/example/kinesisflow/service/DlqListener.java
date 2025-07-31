package org.example.kinesisflow.service;

import lombok.Data;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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


    private final List<ConsumerRecord<String, String>> dlqRecords = new CopyOnWriteArrayList<>();


    @KafkaListener(
            id = "dlq-listener",
            topics = "raw-market-data-DLQ",
            groupId = "dlq-processor-group"
    )
    public void listenDlq(ConsumerRecord<String, String> record) {
        log.warn("DLQ MESSAGE RECEIVED: Key='{}', Payload='{}', Headers='{}'",
                record.key(), record.value(), record.headers());

        dlqRecords.add(record);
    }

    public void clearMessages() {
        dlqRecords.clear();
    }

}