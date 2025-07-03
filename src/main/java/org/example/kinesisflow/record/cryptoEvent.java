package org.example.kinesisflow.record;


public record cryptoEvent(
        String asset,
        java.math.BigDecimal price,
        long timestamp
) {}