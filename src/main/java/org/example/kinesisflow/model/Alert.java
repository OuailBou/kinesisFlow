package org.example.kinesisflow.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal price;

    private String asset;

    // -1, 0, 1
    private int comparisonType;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    public Alert(BigDecimal price, String asset, int comparisonType, User user) {
        this.price = price;
        this.asset = asset;
        this.comparisonType = comparisonType;
        this.user = user;
    }
}
