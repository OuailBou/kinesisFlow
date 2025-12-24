package com.kinesisflow.service;

import com.kinesisflow.record.CryptoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@Profile("dev") // Only run in dev mode
public class MarketDataSimulator {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSimulator.class);
    private final KafkaProducerService producerService;
    private final Random random = new Random();

    // Initial prices
    private final Map<String, BigDecimal> prices = new HashMap<>();

    public MarketDataSimulator(KafkaProducerService producerService) {
        this.producerService = producerService;
        prices.put("BTC", new BigDecimal("45000.00"));
        prices.put("ETH", new BigDecimal("3200.00"));
        prices.put("SOL", new BigDecimal("110.00"));
        prices.put("ADA", new BigDecimal("1.20"));
        prices.put("DOT", new BigDecimal("15.00"));
    }

    @Scheduled(fixedRate = 200) // 5 events per second per asset
    public void simulateMarketData() {
        prices.forEach((symbol, price) -> updateAndSend(symbol, price));
    }

    private void updateAndSend(String symbol, BigDecimal currentPrice) {
        // Random fluctuation between -0.1% and +0.1% to prevent runaway values
        double changePercent = (random.nextDouble() - 0.5) * 0.002;
        BigDecimal change = currentPrice.multiply(BigDecimal.valueOf(changePercent));
        BigDecimal newPrice = currentPrice.add(change).setScale(2, RoundingMode.HALF_UP);

        // Update state
        prices.put(symbol, newPrice);

        CryptoEvent event = new CryptoEvent(symbol, newPrice, Instant.now().toEpochMilli());
        producerService.send(event);
        log.debug("Simulated event: {} - {}", symbol, newPrice);
    }
}
