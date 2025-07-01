package org.example.kinesisflow.record;

public record cryptoEvent(
        String asset,         // ej: "BTC"
        java.math.BigDecimal price, // Usar BigDecimal para dinero
        long timestamp        // Unix timestamp
) {}