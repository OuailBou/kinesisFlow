package com.kinesisflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlertDTO {

    @NotBlank(message = "Asset is required")
    private String asset;

    @Min(value = -1, message = "ComparisonType must be -1, 0 or 1")
    @Max(value = 1, message = "ComparisonType must be -1, 0 or 1")
    private int comparisonType;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;


}
