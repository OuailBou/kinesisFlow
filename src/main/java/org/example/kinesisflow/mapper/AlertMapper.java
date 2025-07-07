package org.example.kinesisflow.mapper;

import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.AlertId;

public class AlertMapper {

    public static AlertDTO toDTO(Alert alert) {
        if (alert == null) return null;

        AlertDTO dto = new AlertDTO();
        if (alert.getId() != null) {
            dto.setAsset(alert.getId().getAsset());
            dto.setPrice(alert.getId().getPrice());
            dto.setComparisonType(alert.getId().getComparisonType());
        }
        return dto;
    }

    public static Alert fromDTO(AlertDTO dto) {
        if (dto == null) return null;

        AlertId alertId = new AlertId(
                dto.getPrice(),
                dto.getAsset(),
                dto.getComparisonType()
        );

        Alert alert = new Alert();
        alert.setId(alertId);
        return alert;
    }
    public static AlertId toId(AlertDTO dto) {
        if (dto == null) return null;


        return new AlertId(
                dto.getPrice(),
                dto.getAsset(),
                dto.getComparisonType()
        );
    }
}
