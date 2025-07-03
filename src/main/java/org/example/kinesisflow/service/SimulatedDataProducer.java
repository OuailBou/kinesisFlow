package org.example.kinesisflow.service;
import org.example.kinesisflow.record.cryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SimulatedDataProducer {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDataProducer.class);
    private final KafkaProducerService kafkaProducerService;
    private final Random random = new Random();

    private final AtomicReference<BigDecimal> btcPrice = new AtomicReference<>(new BigDecimal("68000.00"));
    private final AtomicReference<BigDecimal> ethPrice = new AtomicReference<>(new BigDecimal("3500.00"));

    @Autowired
    public SimulatedDataProducer(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Scheduled(fixedRate = 30000)
    public void generateAndSendData() {
        // Genera un pequeño cambio aleatorio para simular la volatilidad
        BigDecimal btcChange = BigDecimal.valueOf((random.nextDouble() - 0.5) * 100); // Cambio entre -50 y +50
        BigDecimal newBtcPrice = btcPrice.updateAndGet(price -> price.add(btcChange).setScale(2, RoundingMode.HALF_UP));

        BigDecimal ethChange = BigDecimal.valueOf((random.nextDouble() - 0.5) * 10); // Cambio entre -5 y +5
        BigDecimal newEthPrice = ethPrice.updateAndGet(price -> price.add(ethChange).setScale(2, RoundingMode.HALF_UP));

        List<cryptoEvent> events = List.of(
                new cryptoEvent("BTC", newBtcPrice, System.currentTimeMillis()),
                new cryptoEvent("ETH", newEthPrice, System.currentTimeMillis())
        );

        events.forEach(event -> {
            log.info("Generating simulated data: {}", event);
            kafkaProducerService.send(event);
        });
    }
}