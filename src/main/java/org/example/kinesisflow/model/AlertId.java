package org.example.kinesisflow.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
@Data
@NoArgsConstructor
public class AlertId implements Serializable {

    private Double price;
    private String asset;
    private int comparisonType;

    public AlertId(Double price, String asset, int comparisonType) {
        this.price = price;
        this.asset = asset;
        this.comparisonType = comparisonType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertId)) return false;
        AlertId alertId = (AlertId) o;
        return comparisonType == alertId.comparisonType &&
                Objects.equals(price, alertId.price) &&
                Objects.equals(asset, alertId.asset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, asset, comparisonType);
    }
}
