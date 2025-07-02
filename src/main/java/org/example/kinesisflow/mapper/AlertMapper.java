package org.example.kinesisflow.mapper;

import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.model.Alert;

public class AlertMapper {

    public static AlertDTO toDTO(Alert alert) {
        if (alert == null) return null;
        AlertDTO dto = new AlertDTO();
        dto.setId(alert.getId());
        dto.setAsset(alert.getAsset());
        dto.setPrice(alert.getPrice());
        dto.setComparisonType(alert.getComparisonType());
        return dto;
    }

    public static Alert fromDTO(AlertDTO dto) {
        if (dto == null) return null;
        Alert alert = new Alert();
        alert.setId(dto.getId());
        alert.setAsset(dto.getAsset());
        alert.setPrice(dto.getPrice());
        alert.setComparisonType(dto.getComparisonType());
        return alert;
    }
}
