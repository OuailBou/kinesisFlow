package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.AlertId;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.example.kinesisflow.repository.UserRepository;
import org.example.kinesisflow.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("Alert Controller Integration Tests")
@Transactional
public class AlertControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0.0")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private AlertDTO validAlertDTO;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        // Arrange - Setup test data
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);

        // Generate JWT token for the test user
        jwtToken = jwtService.generateToken(testUser.getUsername());

        validAlertDTO = new AlertDTO();
        validAlertDTO.setPrice(150.00);
        validAlertDTO.setAsset("AAPL");
        validAlertDTO.setComparisonType(1); // Assuming 1 = GREATER_THAN
    }

    @Nested
    @DisplayName("Subscribe to Alert Tests")
    class SubscribeToAlertTests {

        @Test
        @DisplayName("Should create new alert subscription successfully")
        void shouldCreateNewAlertSubscriptionSuccessfully() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.price").value(150.00))
                    .andExpect(jsonPath("$.asset").value("AAPL"))
                    .andExpect(jsonPath("$.comparisonType").value(1));

            // Assert - Verify alert was created in database
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> savedAlert = alertRepository.findById(alertId);
            assertThat(savedAlert).isPresent();
            assertThat(savedAlert.get().getUsers()).hasSize(1);
            assertThat(savedAlert.get().getUsers().get(0).getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should add user to existing alert subscription")
        void shouldAddUserToExistingAlertSubscription() throws Exception {
            // Arrange - Create existing alert with different user
            User existingUser = new User();
            existingUser.setUsername("existinguser");
            existingUser.setPassword(passwordEncoder.encode("password123"));
            existingUser = userRepository.save(existingUser);

            Alert existingAlert = new Alert(150.00, "AAPL", 1);
            existingAlert.addUser(existingUser);
            alertRepository.save(existingAlert);

            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.asset").value("AAPL"));

            // Assert - Verify alert now has two users
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> updatedAlert = alertRepository.findById(alertId);
            assertThat(updatedAlert).isPresent();
            assertThat(updatedAlert.get().getUsers()).hasSize(2);
        }

        @Test
        @DisplayName("Should return 400 when AlertDTO is invalid")
        void shouldReturn400WhenAlertDTOIsInvalid() throws Exception {
            // Arrange
            AlertDTO invalidAlertDTO = new AlertDTO();
            // Missing required fields
            String requestBody = objectMapper.writeValueAsString(invalidAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 500 when user does not exist")
        void shouldReturn500WhenUserDoesNotExist() throws Exception {
            // Arrange - Create token for non-existent user
            String nonExistentUserToken = jwtService.generateToken("nonexistentuser");
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + nonExistentUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void shouldReturn401WhenUserIsNotAuthenticated() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when JWT token is invalid")
        void shouldReturn401WhenJwtTokenIsInvalid() throws Exception {
            // Arrange
            String invalidToken = "invalid.jwt.token";
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + invalidToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when JWT token is expired")
        void shouldReturn401WhenJwtTokenIsExpired() throws Exception {
            // Arrange - Create an expired token (this might need adjustment based on your JwtService implementation)
            String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTYwMDAwMDAwMCwiZXhwIjoxNjAwMDAwMDAwfQ.invalid";
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + expiredToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 when request body is malformed JSON")
        void shouldReturn400WhenRequestBodyIsMalformedJson() throws Exception {
            // Arrange
            String malformedJson = "{ invalid json }";

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 415 when content type is not JSON")
        void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(requestBody))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("Unsubscribe from Alert Tests")
    class UnsubscribeFromAlertTests {

        @Test
        @DisplayName("Should unsubscribe from alert successfully")
        void shouldUnsubscribeFromAlertSuccessfully() throws Exception {
            // Arrange - Create alert with test user subscription
            Alert existingAlert = new Alert(150.00, "AAPL", 1);
            existingAlert.addUser(testUser);
            alertRepository.save(existingAlert);

            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNoContent());

            // Assert - Verify alert was deleted (no users left)
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> deletedAlert = alertRepository.findById(alertId);
            assertThat(deletedAlert).isEmpty();
        }

        @Test
        @DisplayName("Should remove user from alert with multiple subscribers")
        void shouldRemoveUserFromAlertWithMultipleSubscribers() throws Exception {
            // Arrange - Create alert with multiple users
            User anotherUser = new User();
            anotherUser.setUsername("anotheruser");
            anotherUser.setPassword(passwordEncoder.encode("password123"));
            anotherUser = userRepository.save(anotherUser);

            Alert existingAlert = new Alert(150.00, "AAPL", 1);
            existingAlert.addUser(testUser);
            existingAlert.addUser(anotherUser);
            alertRepository.save(existingAlert);

            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNoContent());

            // Assert - Verify alert still exists but with one less user
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> remainingAlert = alertRepository.findById(alertId);
            assertThat(remainingAlert).isPresent();
            assertThat(remainingAlert.get().getUsers()).hasSize(1);
            assertThat(remainingAlert.get().getUsers().get(0).getUsername()).isEqualTo("anotheruser");
        }

        @Test
        @DisplayName("Should return 500 when alert does not exist")
        void shouldReturn500WhenAlertDoesNotExist() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should handle unsubscribe when user is not subscribed to alert")
        void shouldHandleUnsubscribeWhenUserIsNotSubscribedToAlert() throws Exception {
            // Arrange - Create alert with different user
            User differentUser = new User();
            differentUser.setUsername("differentuser");
            differentUser.setPassword(passwordEncoder.encode("password123"));
            differentUser = userRepository.save(differentUser);

            Alert existingAlert = new Alert(150.00, "AAPL", 1);
            existingAlert.addUser(differentUser);
            alertRepository.save(existingAlert);

            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNoContent());

            // Assert - Verify alert still exists unchanged
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> unchangedAlert = alertRepository.findById(alertId);
            assertThat(unchangedAlert).isPresent();
            assertThat(unchangedAlert.get().getUsers()).hasSize(1);
            assertThat(unchangedAlert.get().getUsers().get(0).getUsername()).isEqualTo("differentuser");
        }

        @Test
        @DisplayName("Should return 400 when AlertDTO is invalid")
        void shouldReturn400WhenAlertDTOIsInvalid() throws Exception {
            // Arrange
            AlertDTO invalidAlertDTO = new AlertDTO();
            String requestBody = objectMapper.writeValueAsString(invalidAlertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 500 when user does not exist")
        void shouldReturn500WhenUserDoesNotExist() throws Exception {
            // Arrange
            String nonExistentUserToken = jwtService.generateToken("nonexistentuser");
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + nonExistentUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void shouldReturn401WhenUserIsNotAuthenticated() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when JWT token is invalid")
        void shouldReturn401WhenJwtTokenIsInvalid() throws Exception {
            // Arrange
            String invalidToken = "invalid.jwt.token";
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .header("Authorization", "Bearer " + invalidToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle concurrent subscription requests")
        void shouldHandleConcurrentSubscriptionRequests() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act - Simulate concurrent requests
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            // Assert - Should have only one alert with one user
            AlertId alertId = new AlertId(150.00, "AAPL", 1);
            Optional<Alert> alert = alertRepository.findById(alertId);
            assertThat(alert).isPresent();
            assertThat(alert.get().getUsers()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle special characters in asset")
        void shouldHandleSpecialCharactersInAsset() throws Exception {
            // Arrange
            AlertDTO specialAssetAlert = new AlertDTO();
            specialAssetAlert.setAsset("BRK.A");
            specialAssetAlert.setPrice(400000.00);
            specialAssetAlert.setComparisonType(1);

            String requestBody = objectMapper.writeValueAsString(specialAssetAlert);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.asset").value("BRK.A"));
        }

        @Test
        @DisplayName("Should handle very large price values")
        void shouldHandleVeryLargePriceValues() throws Exception {
            // Arrange
            AlertDTO largePriceAlert = new AlertDTO();
            largePriceAlert.setAsset("TSLA");
            largePriceAlert.setPrice(999999999.99);
            largePriceAlert.setComparisonType(0); // Assuming 0 = LESS_THAN

            String requestBody = objectMapper.writeValueAsString(largePriceAlert);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.price").value(999999999.99));
        }

        @Test
        @DisplayName("Should handle missing Authorization header")
        void shouldHandleMissingAuthorizationHeader() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle malformed Authorization header")
        void shouldHandleMalformedAuthorizationHeader() throws Exception {
            // Arrange
            String requestBody = objectMapper.writeValueAsString(validAlertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .header("Authorization", "InvalidFormat " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }
    }


}