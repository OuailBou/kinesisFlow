package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.mapper.UserMapper;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.example.kinesisflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

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

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        alertRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Access without token returns 403 forbidden")
    void accessWithoutToken_shouldReturnUnauthorized() throws Exception {
        // Arrange - No token provided

        // Act & Assert
        mockMvc.perform(get("/alerts"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/alerts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/alerts/1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/alerts/1"))
                .andExpect(status().isForbidden());
    }

    @Nested
    @DisplayName("Authenticated requests")
    class AuthenticatedRequests {
        private String jwtToken;
        private User testUser;

        @BeforeEach
        void setupAuthentication() throws Exception {
            String uniqueUsername = "testuser";

            UserDTO userDTO = new UserDTO();
            userDTO.setUsername(uniqueUsername);
            userDTO.setPassword("testpassword");

            mockMvc.perform(post("/auth/addNewUser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userDTO)))
                    .andExpect(status().isCreated());

            userRepository.flush();

            testUser = userRepository.findByUsername(uniqueUsername)
                    .orElseThrow(() -> new RuntimeException("User not found after creation"));

            var authDTO = new UserDTO();
            authDTO.setUsername(uniqueUsername);
            authDTO.setPassword("testpassword");

            var authResult = mockMvc.perform(post("/auth/authenticate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authDTO)))
                    .andExpect(status().isOk())
                    .andReturn();

            jwtToken = authResult.getResponse().getContentAsString();
        }

        @Test
        @DisplayName("Create alert returns created alert with valid data")
        void createAlert_shouldReturnCreatedAlert() throws Exception {
            // Arrange
            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            String alertJson = objectMapper.writeValueAsString(alert);

            // Act
            MvcResult mvcResult = mockMvc.perform(post("/alerts")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alertJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            String responseJson = mvcResult.getResponse().getContentAsString();
            Alert createdAlert = objectMapper.readValue(responseJson, Alert.class);

            assertThat(createdAlert.getId()).isNotNull();
            assertThat(createdAlert.getAsset()).isEqualTo("BTC");
            assertThat(createdAlert.getPrice()).isEqualByComparingTo("70000.00");
            assertThat(createdAlert.getComparisonType()).isEqualTo(1);

            Alert savedAlert = alertRepository.findById(createdAlert.getId()).orElseThrow();
            assertThat(savedAlert.getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Create alert returns 400 with invalid data")
        void createAlert_shouldReturnBadRequest_withInvalidData() throws Exception {
            // Arrange
            String invalidJson = "{}"; // Missing required fields

            // Act & Assert
            mockMvc.perform(post("/alerts")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Create alert handles user with null alerts list")
        void createAlert_shouldHandleNullAlertsList() throws Exception {
            // Arrange
            testUser.setAlerts(null);
            userRepository.save(testUser);

            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            String alertJson = objectMapper.writeValueAsString(alert);

            // Act
            MvcResult mvcResult = mockMvc.perform(post("/alerts")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alertJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            String responseJson = mvcResult.getResponse().getContentAsString();
            Alert createdAlert = objectMapper.readValue(responseJson, Alert.class);

            assertThat(createdAlert.getId()).isNotNull();
            assertThat(createdAlert.getAsset()).isEqualTo("BTC");
        }

        @Test
        @DisplayName("Get all alerts returns only user's alerts")
        void getAllAlerts_shouldReturnOnlyUserAlerts() throws Exception {
            // Arrange
            Alert alert1 = new Alert();
            alert1.setAsset("ETH");
            alert1.setPrice(new BigDecimal("3000.00"));
            alert1.setComparisonType(1);
            alert1.setUser(testUser);
            alertRepository.save(alert1);

            UserDTO userDTO = new UserDTO();
            userDTO.setUsername("user777");
            userDTO.setPassword("testpassword");
            User otherUser = UserMapper.toEntity(userDTO);
            userRepository.save(otherUser);

            Alert alert2 = new Alert();
            alert2.setAsset("BTC");
            alert2.setPrice(new BigDecimal("70000.00"));
            alert2.setComparisonType(0);
            alert2.setUser(otherUser);
            alertRepository.save(alert2);

            otherUser.setAlerts(List.of(alert2));

            // Act & Assert
            mockMvc.perform(get("/alerts")
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].asset").value("ETH"));
        }

        @Test
        @DisplayName("Get all alerts returns empty list when user has no alerts")
        void getAllAlerts_shouldReturnEmptyList() throws Exception {
            // Arrange - No alerts for user

            // Act & Assert
            mockMvc.perform(get("/alerts")
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Get alert by ID returns alert when user owns it")
        void getAlertById_shouldReturnAlert_whenUserOwnsIt() throws Exception {
            // Arrange
            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            alert.setUser(testUser);
            Alert saved = alertRepository.save(alert);

            // Act & Assert
            mockMvc.perform(get("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.asset").value("BTC"))
                    .andExpect(jsonPath("$.price").value(70000.00))
                    .andExpect(jsonPath("$.comparisonType").value(1));
        }

        @Test
        @DisplayName("Get alert by ID returns 403 when user doesn't own it")
        void getAlertById_shouldReturnForbidden_whenUserDoesNotOwnIt() throws Exception {
            // Arrange
            User otherUser = new User();
            otherUser.setUsername("user2");
            otherUser.setPassword("otherpassword");
            userRepository.save(otherUser);

            Alert otherUserAlert = new Alert();
            otherUserAlert.setAsset("BTC");
            otherUserAlert.setPrice(new BigDecimal("70000.00"));
            otherUserAlert.setComparisonType(1);
            otherUserAlert.setUser(otherUser);
            Alert saved = alertRepository.save(otherUserAlert);

            // Act & Assert
            mockMvc.perform(get("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Get alert by ID returns 403 when alert doesn't exist")
        void getAlertById_shouldReturnForbidden_whenAlertDoesNotExist() throws Exception {
            // Arrange
            Long nonExistentId = 999L;

            // Act & Assert
            mockMvc.perform(get("/alerts/{id}", nonExistentId)
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Update alert modifies existing alert when user owns it")
        void updateAlert_shouldModifyExistingAlert_whenUserOwnsIt() throws Exception {
            // Arrange
            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            alert.setUser(testUser);
            Alert saved = alertRepository.save(alert);

            Alert updatedAlert = new Alert();
            updatedAlert.setAsset("BTC");
            updatedAlert.setPrice(new BigDecimal("75000.00"));
            updatedAlert.setComparisonType(0);
            String updatedJson = objectMapper.writeValueAsString(updatedAlert);

            // Act
            MvcResult mvcResult = mockMvc.perform(put("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatedJson))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert
            String responseJson = mvcResult.getResponse().getContentAsString();
            Alert responseAlert = objectMapper.readValue(responseJson, Alert.class);

            assertThat(responseAlert.getPrice()).isEqualByComparingTo("75000.00");
            assertThat(responseAlert.getComparisonType()).isEqualTo(0);

            Alert fromDb = alertRepository.findById(saved.getId()).orElseThrow();
            assertThat(fromDb.getPrice()).isEqualByComparingTo("75000.00");
            assertThat(fromDb.getComparisonType()).isEqualTo(0);
        }

        @Test
        @DisplayName("Update alert returns 403 when user doesn't own it")
        void updateAlert_shouldReturnForbidden_whenUserDoesNotOwnIt() throws Exception {
            // Arrange
            User otherUser = new User();
            otherUser.setUsername("user3");
            otherUser.setPassword("otherpassword");
            userRepository.save(otherUser);

            Alert otherUserAlert = new Alert();
            otherUserAlert.setAsset("BTC");
            otherUserAlert.setPrice(new BigDecimal("70000.00"));
            otherUserAlert.setComparisonType(1);
            otherUserAlert.setUser(otherUser);
            Alert saved = alertRepository.save(otherUserAlert);

            Alert updatedAlert = new Alert();
            updatedAlert.setAsset("BTC");
            updatedAlert.setPrice(new BigDecimal("80000.00"));
            updatedAlert.setComparisonType(0);
            String updatedJson = objectMapper.writeValueAsString(updatedAlert);

            // Act & Assert
            mockMvc.perform(put("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatedJson))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Update alert returns 403 when alert doesn't exist")
        void updateAlert_shouldReturnForbidden_whenAlertDoesNotExist() throws Exception {
            // Arrange
            Long nonExistentId = 999L;
            Alert updatedAlert = new Alert();
            updatedAlert.setAsset("BTC");
            updatedAlert.setPrice(new BigDecimal("80000.00"));
            updatedAlert.setComparisonType(0);
            String updatedJson = objectMapper.writeValueAsString(updatedAlert);

            // Act & Assert
            mockMvc.perform(put("/alerts/{id}", nonExistentId)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatedJson))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Update alert returns 400 with invalid data")
        void updateAlert_shouldReturnBadRequest_withInvalidData() throws Exception {
            // Arrange
            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            alert.setUser(testUser);
            Alert saved = alertRepository.save(alert);

            String invalidJson = "{}"; // Missing required fields

            // Act & Assert
            mockMvc.perform(put("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Delete alert removes alert when user owns it")
        void deleteAlert_shouldRemoveAlert_whenUserOwnsIt() throws Exception {
            // Arrange
            Alert alert = new Alert();
            alert.setAsset("BTC");
            alert.setPrice(new BigDecimal("70000.00"));
            alert.setComparisonType(1);
            alert.setUser(testUser);
            Alert saved = alertRepository.save(alert);

            // Act
            mockMvc.perform(delete("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isNoContent());

            // Assert
            assertThat(alertRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("Delete alert returns 403 when user doesn't own it")
        void deleteAlert_shouldReturnForbidden_whenUserDoesNotOwnIt() throws Exception {
            // Arrange
            User otherUser = new User();
            otherUser.setUsername("user4");
            otherUser.setPassword("otherpassword");
            userRepository.save(otherUser);

            Alert otherUserAlert = new Alert();
            otherUserAlert.setAsset("BTC");
            otherUserAlert.setPrice(new BigDecimal("70000.00"));
            otherUserAlert.setComparisonType(1);
            otherUserAlert.setUser(otherUser);
            Alert saved = alertRepository.save(otherUserAlert);

            // Act
            mockMvc.perform(delete("/alerts/{id}", saved.getId())
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isForbidden());

            // Assert
            assertThat(alertRepository.findById(saved.getId())).isPresent();
        }

        @Test
        @DisplayName("Delete alert returns 403 when alert doesn't exist")
        void deleteAlert_shouldReturnForbidden_whenAlertDoesNotExist() throws Exception {
            // Arrange
            Long nonExistentId = 999L;

            // Act & Assert
            mockMvc.perform(delete("/alerts/{id}", nonExistentId)
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Multiple alerts operations work correctly")
        void multipleAlertsOperations_shouldWorkCorrectly() throws Exception {
            // Arrange
            Alert alert1 = new Alert();
            alert1.setAsset("BTC");
            alert1.setPrice(new BigDecimal("70000.00"));
            alert1.setComparisonType(1);

            Alert alert2 = new Alert();
            alert2.setAsset("ETH");
            alert2.setPrice(new BigDecimal("3000.00"));
            alert2.setComparisonType(0);

            // Act - Create first alert
            MvcResult createResult1 = mockMvc.perform(post("/alerts")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alert1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            Alert createdAlert1 = objectMapper.readValue(
                    createResult1.getResponse().getContentAsString(), Alert.class);

            // Act - Create second alert
            MvcResult createResult2 = mockMvc.perform(post("/alerts")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alert2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            Alert createdAlert2 = objectMapper.readValue(
                    createResult2.getResponse().getContentAsString(), Alert.class);

            // Act - Get all alerts
            mockMvc.perform(get("/alerts")
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            // Act - Update first alert
            Alert updatedAlert1 = new Alert();
            updatedAlert1.setAsset("BTC");
            updatedAlert1.setPrice(new BigDecimal("75000.00"));
            updatedAlert1.setComparisonType(0);

            mockMvc.perform(put("/alerts/{id}", createdAlert1.getId())
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updatedAlert1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(75000.00))
                    .andExpect(jsonPath("$.comparisonType").value(0));

            // Act - Delete second alert
            mockMvc.perform(delete("/alerts/{id}", createdAlert2.getId())
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isNoContent());

            // Assert - Final state verification
            mockMvc.perform(get("/alerts")
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].asset").value("BTC"))
                    .andExpect(jsonPath("$[0].price").value(75000.00));
        }
    }
}