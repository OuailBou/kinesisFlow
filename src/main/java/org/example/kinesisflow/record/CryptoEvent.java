package org.example.kinesisflow.record;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CryptoEvent(
        @NotBlank(message = "Asset must not be blank")
        String asset,

        @NotNull(message = "Price must not be null")
        @Positive(message = "Price must be positive")
        BigDecimal price,

        long timestamp
) {}