package org.example.kinesisflow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal price;

    private String asset;

    // Guardamos -1, 0, 1 directamente
    private int comparisonType;

    public Alert() {}

    public Alert(BigDecimal price, String asset, int comparisonType) {
        this.price = price;
        this.asset = asset;
        this.comparisonType = comparisonType;
    }
}
