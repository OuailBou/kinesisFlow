package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.model.Alert;
import org.example.kinesisflow.model.AlertId;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.AlertRepository;
import org.example.kinesisflow.repository.UserRepository;
import org.example.kinesisflow.service.JwtService;
import org.example.kinesisflow.service.RedisSortedSetService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("Alert Controller Integration Tests")
class AlertControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisSortedSetService redisSortedSetService;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setup() {
        alertRepository.deleteAll();
        userRepository.deleteAll();

        // Clear Redis data
        redisSortedSetService.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);

        // Generate JWT token
        jwtToken = jwtService.generateToken(testUser.getUsername());
    }

    @Nested
    @DisplayName("Alert Subscription (/api/alerts/subscribe)")
    class AlertSubscriptionTests {

        @Test
        @DisplayName("Should subscribe to alert and add to Redis")
        void shouldSubscribeToAlertAndAddToRedis() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("BTC", 1, 50000.0); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act
            MvcResult result = mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.asset").value("BTC"))
                    .andExpect(jsonPath("$.comparisonType").value(1))
                    .andExpect(jsonPath("$.price").value(50000.0))
                    .andReturn();

            // Assert - Database
            AlertId alertId = new AlertId(50000.0, "BTC", 1);
            Alert savedAlert = alertRepository.findById(alertId).orElse(null);
            assertThat(savedAlert).isNotNull();
            assertThat(savedAlert.getUsers()).hasSize(1);
            assertThat(savedAlert.getUsers().getFirst().getUsername()).isEqualTo("testuser");

            // Assert - Redis
            String redisKey = "BTC:1";
            String redisValue = "BTC:1:50000.0";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).contains(redisValue);

            Double score = redisSortedSetService.getScore(redisKey, redisValue);
            assertThat(score).isEqualTo(50000.0);
        }

        @Test
        @DisplayName("Should subscribe multiple users to same alert without duplicating Redis entry")
        void shouldSubscribeMultipleUsersWithoutDuplicatingRedis() throws Exception {
            // Arrange - Create second user
            User secondUser = new User();
            secondUser.setUsername("seconduser");
            secondUser.setPassword(passwordEncoder.encode("password123"));
            secondUser = userRepository.save(secondUser);
            String secondToken = jwtService.generateToken(secondUser.getUsername());

            AlertDTO alertDTO = new AlertDTO("ETH", 0, 2000.0); // 0 = BELOW
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act - First user subscribes
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Act - Second user subscribes to same alert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + secondToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Assert - Database
            AlertId alertId = new AlertId(2000.0, "ETH", 0);
            Alert savedAlert = alertRepository.findById(alertId).orElse(null);
            assertThat(savedAlert).isNotNull();
            assertThat(savedAlert.getUsers()).hasSize(2);

            // Assert - Redis (should have only one entry)
            String redisKey = "ETH:0";
            String redisValue = "ETH:0:2000.0";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(1);
            assertThat(redisElements).contains(redisValue);
        }

        @Test
        @DisplayName("Should handle subscription to existing alert for same user")
        void shouldHandleSubscriptionToExistingAlertForSameUser() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("ADA", 1, 1.5); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act - First subscription
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Act - Second subscription (same user, same alert)
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Assert - Database (should still have one user)
            AlertId alertId = new AlertId(1.5, "ADA", 1);
            Alert savedAlert = alertRepository.findById(alertId).orElse(null);
            assertThat(savedAlert).isNotNull();
            assertThat(savedAlert.getUsers()).hasSize(1);

            // Assert - Redis (should still have one entry)
            String redisKey = "ADA:1";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(1);
        }

        @Test
        @DisplayName("Should return 403 when subscribing without authentication")
        void shouldReturnUnauthorizedWithoutAuth() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("BTC", 1, 50000.0); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isForbidden());

            // Assert - No data in database or Redis
            assertThat(alertRepository.findAll()).isEmpty();
            assertThat(redisSortedSetService.getAllKeys()).isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when subscribing with invalid data")
        void shouldReturnBadRequestForInvalidData() throws Exception {
            // Arrange
            String invalidJson = "{\"asset\": \"\", \"comparisonType\": 999, \"price\": -1}";

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            // Assert - No data in database or Redis
            assertThat(alertRepository.findAll()).isEmpty();
            assertThat(redisSortedSetService.getAllKeys()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Alert Unsubscription (/api/alerts/unsubscribe)")
    class AlertUnsubscriptionTests {

        @Test
        @DisplayName("Should unsubscribe from alert and remove from Redis when last user")
        void shouldUnsubscribeAndRemoveFromRedisWhenLastUser() throws Exception {
            // Arrange - Create and subscribe to alert
            AlertDTO alertDTO = new AlertDTO("BTC", 1, 50000.0); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Verify alert exists in Redis
            String redisKey = "BTC:1";
            assertThat(redisSortedSetService.getAllElements(redisKey)).isNotEmpty();

            // Act - Unsubscribe
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            // Assert - Database
            AlertId alertId = new AlertId(50000.0, "BTC", 1);
            assertThat(alertRepository.findById(alertId)).isEmpty();

            // Assert - Redis (key should be deleted)
            assertThat(redisSortedSetService.getAllElements(redisKey)).isEmpty();
        }

        @Test
        @DisplayName("Should unsubscribe without affecting Redis when other users remain")
        void shouldUnsubscribeWithoutAffectingRedisWhenOtherUsersRemain() throws Exception {
            // Arrange - Create second user
            User secondUser = new User();
            secondUser.setUsername("seconduser");
            secondUser.setPassword(passwordEncoder.encode("password123"));
            secondUser = userRepository.save(secondUser);
            String secondToken = jwtService.generateToken(secondUser.getUsername());

            AlertDTO alertDTO = new AlertDTO("ETH", 0, 2000.0); // 0 = BELOW
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Subscribe both users
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + secondToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Act - First user unsubscribes
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            // Assert - Database (alert should still exist with second user)
            AlertId alertId = new AlertId(2000.0, "ETH", 0);
            Alert remainingAlert = alertRepository.findById(alertId).orElse(null);
            assertThat(remainingAlert).isNotNull();
            assertThat(remainingAlert.getUsers()).hasSize(1);
            assertThat(remainingAlert.getUsers().iterator().next().getUsername()).isEqualTo("seconduser");

            // Assert - Redis (should still exist)
            String redisKey = "ETH:0";
            String redisValue = "ETH:0:2000.0";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).contains(redisValue);
        }


        @Test
        @DisplayName("Should return 403 when unsubscribing without authentication")
        void shouldReturnUnauthorizedWithoutAuth() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("BTC", 1, 50000.0); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 400 when unsubscribing with invalid data")
        void shouldReturnBadRequestForInvalidData() throws Exception {
            // Arrange
            String invalidJson = "{\"asset\": \"\", \"comparisonType\": 999, \"price\": -1}";

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Complete Alert Lifecycle with Redis")
    class CompleteAlertLifecycleTests {

        @Test
        @DisplayName("Should complete full alert lifecycle: subscribe → verify Redis → unsubscribe → verify Redis cleanup")
        void shouldCompleteFullAlertLifecycle() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("DOGE", 1, 0.1); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Act - Subscribe
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Assert - Verify Redis entry created
            String redisKey = "DOGE:1";
            String redisValue = "DOGE:1:0.1";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).contains(redisValue);
            assertThat(redisSortedSetService.getScore(redisKey, redisValue)).isEqualTo(0.1);

            // Act - Unsubscribe
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            // Assert - Verify Redis cleanup
            assertThat(redisSortedSetService.getAllElements(redisKey)).isEmpty();
            assertThat(alertRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Should handle multiple alerts with different assets and comparison types")
        void shouldHandleMultipleAlertsWithDifferentKeysInRedis() throws Exception {
            // Arrange
            AlertDTO alert1 = new AlertDTO("BTC", 1, 50000.0); // 1 = ABOVE
            AlertDTO alert2 = new AlertDTO("BTC", 0, 45000.0); // 0 = BELOW
            AlertDTO alert3 = new AlertDTO("ETH", 1, 3000.0); // 1 = ABOVE

            String request1 = objectMapper.writeValueAsString(alert1);
            String request2 = objectMapper.writeValueAsString(alert2);
            String request3 = objectMapper.writeValueAsString(alert3);

            // Act - Subscribe to all alerts
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(request1))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(request2))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(request3))
                    .andExpect(status().isCreated());

            // Assert - Verify all Redis entries
            assertThat(redisSortedSetService.getAllElements("BTC:1")).contains("BTC:1:50000.0");
            assertThat(redisSortedSetService.getAllElements("BTC:0")).contains("BTC:0:45000.0");
            assertThat(redisSortedSetService.getAllElements("ETH:1")).contains("ETH:1:3000.0");

            // Act - Unsubscribe from one alert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(request1))
                    .andExpect(status().isNoContent());

            // Assert - Verify selective Redis cleanup
            assertThat(redisSortedSetService.getAllElements("BTC:1")).isEmpty();
            assertThat(redisSortedSetService.getAllElements("BTC:0")).contains("BTC:0:45000.0");
            assertThat(redisSortedSetService.getAllElements("ETH:1")).contains("ETH:1:3000.0");
        }

        @Test
        @DisplayName("Should handle concurrent subscriptions and unsubscriptions with Redis consistency")
        void shouldHandleConcurrentOperationsWithRedisConsistency() throws Exception {
            // Arrange
            AlertDTO alertDTO = new AlertDTO("LTC", 1, 150.0); // 1 = ABOVE
            String requestJson = objectMapper.writeValueAsString(alertDTO);

            // Create multiple users
            User user2 = new User();
            user2.setUsername("user2");
            user2.setPassword(passwordEncoder.encode("password123"));
            user2 = userRepository.save(user2);

            User user3 = new User();
            user3.setUsername("user3");
            user3.setPassword(passwordEncoder.encode("password123"));
            user3 = userRepository.save(user3);

            String token2 = jwtService.generateToken(user2.getUsername());
            String token3 = jwtService.generateToken(user3.getUsername());

            // Act - Multiple users subscribe
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token2)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token3)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Assert - Redis should have one entry
            String redisKey = "LTC:1";
            String redisValue = "LTC:1:150.0";
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(1);
            assertThat(redisElements).contains(redisValue);

            // Act - Two users unsubscribe
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + jwtToken)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token2)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            // Assert - Redis should still have the entry (one user remains)
            assertThat(redisSortedSetService.getAllElements(redisKey)).contains(redisValue);

            // Act - Last user unsubscribe
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token3)
                            .content(requestJson))
                    .andExpect(status().isNoContent());

            // Assert - Redis should be clean
            assertThat(redisSortedSetService.getAllElements(redisKey)).isEmpty();
            assertThat(alertRepository.findAll()).isEmpty();
        }
    }
}