package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AlertControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanup() {
        alertRepository.deleteAll();
    }

    @Test
    @DisplayName("Create alert returns created alert with valid data")
    void createAlert_shouldReturnCreatedAlert() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("70000.00"));
        alert.setComparisonType(1);

        String alertJson = objectMapper.writeValueAsString(alert);

        var mvcResult = mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertJson))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = mvcResult.getResponse().getContentAsString();
        Alert createdAlert = objectMapper.readValue(responseJson, Alert.class);

        assertThat(createdAlert.getId()).isNotNull();
        assertThat(createdAlert.getAsset()).isEqualTo("BTC");
        assertThat(createdAlert.getPrice()).isEqualByComparingTo("70000.00");
        assertThat(createdAlert.getComparisonType()).isEqualTo(1);
        assertThat(alertRepository.findById(createdAlert.getId())).isPresent();
    }

    @Test
    @DisplayName("Get all alerts returns list with alerts")
    void getAllAlerts_shouldReturnListOfAlerts() throws Exception {
        Alert alert1 = new Alert();
        alert1.setAsset("ETH");
        alert1.setPrice(new BigDecimal("3000.00"));
        alert1.setComparisonType(1);
        alertRepository.save(alert1);

        Alert alert2 = new Alert();
        alert2.setAsset("BTC");
        alert2.setPrice(new BigDecimal("70000.00"));
        alert2.setComparisonType(0);
        alertRepository.save(alert2);

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].asset").value("ETH"))
                .andExpect(jsonPath("$[1].asset").value("BTC"));
    }

    @Test
    @DisplayName("Get all alerts returns empty list when no alerts exist")
    void getAllAlerts_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Get alert by ID returns alert when exists")
    void getAlertById_shouldReturnAlert_whenExists() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("70000.00"));
        alert.setComparisonType(1);
        Alert saved = alertRepository.save(alert);

        mockMvc.perform(get("/alerts/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asset").value("BTC"))
                .andExpect(jsonPath("$.price").value(70000.00))
                .andExpect(jsonPath("$.comparisonType").value(1));
    }

    @Test
    @DisplayName("Get alert by ID returns 404 when alert does not exist")
    void getAlertById_shouldReturnNotFound_whenDoesNotExist() throws Exception {
        mockMvc.perform(get("/alerts/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Update alert modifies existing alert successfully")
    void updateAlert_shouldModifyExistingAlert() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("70000.00"));
        alert.setComparisonType(1);
        Alert saved = alertRepository.save(alert);

        saved.setPrice(new BigDecimal("75000.00"));
        saved.setComparisonType(0);
        String updatedJson = objectMapper.writeValueAsString(saved);

        var mvcResult = mockMvc.perform(put("/alerts/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = mvcResult.getResponse().getContentAsString();
        Alert updatedAlert = objectMapper.readValue(responseJson, Alert.class);

        assertThat(updatedAlert.getPrice()).isEqualByComparingTo("75000.00");
        assertThat(updatedAlert.getComparisonType()).isEqualTo(0);

        var fromDb = alertRepository.findById(saved.getId());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getPrice()).isEqualByComparingTo("75000.00");
        assertThat(fromDb.get().getComparisonType()).isEqualTo(0);
    }

    @Test
    @DisplayName("Update alert returns 404 when alert does not exist")
    void updateAlert_shouldReturnNotFound_whenDoesNotExist() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("ETH");
        alert.setPrice(new BigDecimal("3000.00"));
        alert.setComparisonType(1);
        String alertJson = objectMapper.writeValueAsString(alert);

        mockMvc.perform(put("/alerts/{id}", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Delete alert removes alert when exists")
    void deleteAlert_shouldRemoveAlert_whenExists() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("70000.00"));
        alert.setComparisonType(1);
        Alert saved = alertRepository.save(alert);

        mockMvc.perform(delete("/alerts/{id}", saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(alertRepository.findById(saved.getId())).isEmpty();
        assertThat(alertRepository.count()).isZero();
    }

    @Test
    @DisplayName("Delete alert returns 404 when alert does not exist")
    void deleteAlert_shouldReturnNotFound_whenDoesNotExist() throws Exception {
        mockMvc.perform(delete("/alerts/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Create alert returns 400 when asset is blank")
    void createAlert_shouldReturnBadRequest_whenAssetIsBlank() throws Exception {
        Alert alert = new Alert();
        alert.setAsset(""); // Invalid: blank
        alert.setPrice(new BigDecimal("50000.00"));
        alert.setComparisonType(1);

        String alertJson = objectMapper.writeValueAsString(alert);

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create alert returns 400 when price is negative")
    void createAlert_shouldReturnBadRequest_whenPriceIsNegative() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("-100.00")); // Invalid: negative price
        alert.setComparisonType(1);

        String alertJson = objectMapper.writeValueAsString(alert);

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create alert returns 400 when comparisonType is invalid")
    void createAlert_shouldReturnBadRequest_whenComparisonTypeInvalid() throws Exception {
        Alert alert = new Alert();
        alert.setAsset("BTC");
        alert.setPrice(new BigDecimal("40000.00"));
        alert.setComparisonType(3); // Invalid: not 0 or 1

        String alertJson = objectMapper.writeValueAsString(alert);

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertJson))
                .andExpect(status().isBadRequest());

    }

}
