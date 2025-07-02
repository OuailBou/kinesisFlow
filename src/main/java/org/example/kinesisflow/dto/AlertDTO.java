package org.example.kinesisflow.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertDTO {
    private Long id;
    private BigDecimal price;
    private String asset;
    private int comparisonType;  // -1, 0 o 1
}
