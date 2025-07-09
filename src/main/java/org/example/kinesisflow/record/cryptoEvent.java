package org.example.kinesisflow.record;


import java.math.BigDecimal;

public record cryptoEvent(
        String asset,
        BigDecimal price,
        long timestamp
) {}