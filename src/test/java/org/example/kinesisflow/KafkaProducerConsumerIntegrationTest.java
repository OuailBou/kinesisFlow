package org.example.kinesisflow;

import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.repository.AlertRepository;
import org.example.kinesisflow.service.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class KafkaProducerConsumerIntegrationTest {
    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");



    @Autowired
    private AlertRepository alertRepository;


    @DynamicPropertySource
    public static void initKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaProducerService publisher;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @BeforeEach
    void setupTest() {

        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var container = kafkaListenerEndpointRegistry.getListenerContainer("kinesis-listener");
            return container != null && container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty();
        });
    }



    @Test
    public void testSendEventsToTopic(){
                    publisher.send(new cryptoEvent("SOL", new BigDecimal(300), 123123));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->  assertThat(alertRepository.findAll()).hasSize(2));


    }


}