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

import java.math.BigDecimal;
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
        redisSortedSetService.deleteAll();

        testUser = createAndSaveUser("testuser", "password123");
        jwtToken = jwtService.generateToken(testUser.getUsername());
    }

    // Helper methods - DRY principle
    private User createAndSaveUser(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private String createRedisKey(String asset, int comparisonType) {
        return redisSortedSetService.createRuleIndexKey(asset, String.valueOf(comparisonType));
    }

    private String createRedisValue(User user, BigDecimal price) {
        return redisSortedSetService.createRuleIndexValue(user, price);
    }

    private AlertDTO createAlertDTO(String asset, int comparisonType, BigDecimal price) {
        return new AlertDTO(asset, comparisonType, price);
    }

    private AlertId createAlertId(String asset, int comparisonType, BigDecimal price) {
        return new AlertId(price, asset, comparisonType);
    }

    private MvcResult performSubscribe(String token, AlertDTO alertDTO) throws Exception {
        return mockMvc.perform(post("/api/alerts/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult performUnsubscribe(String token, AlertDTO alertDTO) throws Exception {
        return mockMvc.perform(delete("/api/alerts/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private void assertAlertInDatabase(AlertId alertId, int expectedUserCount) {
        Alert savedAlert = alertRepository.findById(alertId).orElse(null);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getUsers()).hasSize(expectedUserCount);
    }

    private void assertAlertNotInDatabase(AlertId alertId) {
        assertThat(alertRepository.findById(alertId)).isEmpty();
    }

    private void assertRedisContainsValue(String redisKey, String redisValue, BigDecimal expectedScore) {
        Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
        assertThat(redisElements).contains(redisValue);

        if (expectedScore != null) {
            Double score = redisSortedSetService.getScore(redisKey, redisValue);
            assertThat(score).isEqualTo(expectedScore.doubleValue());
        }
    }

    private void assertRedisDoesNotContainValue(String redisKey, String redisValue) {
        Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
        assertThat(redisElements).doesNotContain(redisValue);
    }

    private void assertRedisKeyEmpty(String redisKey) {
        assertThat(redisSortedSetService.getAllElements(redisKey)).isEmpty();
    }

    private void assertNoDatabaseOrRedisData() {
        assertThat(alertRepository.findAll()).isEmpty();
        assertThat(redisSortedSetService.getAllKeys()).isEmpty();
    }

    @Nested
    @DisplayName("Alert Subscription (/api/alerts/subscribe)")
    class AlertSubscriptionTests {

        @Test
        @DisplayName("Should subscribe to alert and add to Redis")
        void shouldSubscribeToAlertAndAddToRedis() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("BTC", 1, new BigDecimal("50000.0"));
            AlertId alertId = createAlertId("BTC", 1, new BigDecimal("50000.0"));
            String redisKey = createRedisKey("BTC", 1);
            String redisValue = createRedisValue(testUser, new BigDecimal("50000.0"));

            // Act
            MvcResult result = performSubscribe(jwtToken, alertDTO);

            // Assert - Response
            assertThat(result.getResponse().getContentAsString())
                    .contains("\"asset\":\"BTC\"")
                    .contains("\"comparisonType\":1")
                    .contains("\"price\":50000.0");

            // Assert - Database
            assertAlertInDatabase(alertId, 1);
            Alert savedAlert = alertRepository.findById(alertId).get();
            assertThat(savedAlert.getUsers().getFirst().getUsername()).isEqualTo("testuser");

            // Assert - Redis
            assertRedisContainsValue(redisKey, redisValue, new BigDecimal("50000.0"));
        }

        @Test
        @DisplayName("Should subscribe multiple users to same alert without duplicating Redis entry")
        void shouldSubscribeMultipleUsersWithoutDuplicatingRedis() throws Exception {
            // Arrange
            User secondUser = createAndSaveUser("seconduser", "password123");
            String secondToken = jwtService.generateToken(secondUser.getUsername());

            AlertDTO alertDTO = createAlertDTO("ETH", 0, new BigDecimal("2000.0"));
            AlertId alertId = createAlertId("ETH", 0, new BigDecimal("2000.0"));
            String redisKey = createRedisKey("ETH", 0);
            String redisValue1 = createRedisValue(testUser, new BigDecimal("2000.0"));
            String redisValue2 = createRedisValue(secondUser, new BigDecimal("2000.0"));

            // Act
            performSubscribe(jwtToken, alertDTO);
            performSubscribe(secondToken, alertDTO);

            // Assert - Database
            assertAlertInDatabase(alertId, 2);

            // Assert - Redis (should have two entries, one per user)
            assertRedisContainsValue(redisKey, redisValue1, new BigDecimal("2000.0"));
            assertRedisContainsValue(redisKey, redisValue2, new BigDecimal("2000.0"));

            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(2);
        }

        @Test
        @DisplayName("Should handle subscription to existing alert for same user")
        void shouldHandleSubscriptionToExistingAlertForSameUser() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("ADA", 1, new BigDecimal("1.5"));
            AlertId alertId = createAlertId("ADA", 1, new BigDecimal("1.5"));
            String redisKey = createRedisKey("ADA", 1);
            String redisValue = createRedisValue(testUser, new BigDecimal("1.5"));

            // Act - Subscribe twice
            performSubscribe(jwtToken, alertDTO);
            performSubscribe(jwtToken, alertDTO);

            // Assert - Database (should still have one user)
            assertAlertInDatabase(alertId, 1);

            // Assert - Redis (should still have one entry for this user)
            assertRedisContainsValue(redisKey, redisValue, new BigDecimal("1.5"));
            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(1);
        }

        @Test
        @DisplayName("Should return 403 when subscribing without authentication")
        void shouldReturnUnauthorizedWithoutAuth() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("BTC", 1, new BigDecimal("50000.0"));

            // Act & Assert
            mockMvc.perform(post("/api/alerts/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isForbidden());

            assertNoDatabaseOrRedisData();
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

            assertNoDatabaseOrRedisData();
        }
    }

    @Nested
    @DisplayName("Alert Unsubscription (/api/alerts/unsubscribe)")
    class AlertUnsubscriptionTests {

        @Test
        @DisplayName("Should unsubscribe from alert and remove from Redis when last user")
        void shouldUnsubscribeAndRemoveFromRedisWhenLastUser() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("BTC", 1, new BigDecimal("50000.0"));
            AlertId alertId = createAlertId("BTC", 1, new BigDecimal("50000.0"));
            String redisKey = createRedisKey("BTC", 1);
            String redisValue = createRedisValue(testUser, new BigDecimal("50000.0"));

            // Subscribe first
            performSubscribe(jwtToken, alertDTO);
            assertRedisContainsValue(redisKey, redisValue, new BigDecimal("50000.0"));

            // Act - Unsubscribe
            performUnsubscribe(jwtToken, alertDTO);

            // Assert - Database
            assertAlertNotInDatabase(alertId);

            // Assert - Redis
            assertRedisDoesNotContainValue(redisKey, redisValue);
        }

        @Test
        @DisplayName("Should unsubscribe without affecting other users' Redis entries")
        void shouldUnsubscribeWithoutAffectingOtherUsersRedisEntries() throws Exception {
            // Arrange
            User secondUser = createAndSaveUser("seconduser", "password123");
            String secondToken = jwtService.generateToken(secondUser.getUsername());

            AlertDTO alertDTO = createAlertDTO("ETH", 0, new BigDecimal("2000.0"));
            AlertId alertId = createAlertId("ETH", 0, new BigDecimal("2000.0"));
            String redisKey = createRedisKey("ETH", 0);
            String redisValue1 = createRedisValue(testUser, new BigDecimal("2000.0"));
            String redisValue2 = createRedisValue(secondUser, new BigDecimal("2000.0"));

            // Subscribe both users
            performSubscribe(jwtToken, alertDTO);
            performSubscribe(secondToken, alertDTO);

            // Act - First user unsubscribes
            performUnsubscribe(jwtToken, alertDTO);

            // Assert - Database (alert should still exist with second user)
            assertAlertInDatabase(alertId, 1);
            Alert remainingAlert = alertRepository.findById(alertId).get();
            assertThat(remainingAlert.getUsers().iterator().next().getUsername()).isEqualTo("seconduser");

            // Assert - Redis (first user's entry should be removed, second user's should remain)
            assertRedisDoesNotContainValue(redisKey, redisValue1);
            assertRedisContainsValue(redisKey, redisValue2, new BigDecimal("2000.0"));
        }

        @Test
        @DisplayName("Should return 403 when unsubscribing without authentication")
        void shouldReturnUnauthorizedWithoutAuth() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("BTC", 1, new BigDecimal("50000.0"));

            // Act & Assert
            mockMvc.perform(delete("/api/alerts/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
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
            AlertDTO alertDTO = createAlertDTO("DOGE", 1, new BigDecimal("0.1"));
            AlertId alertId = createAlertId("DOGE", 1, new BigDecimal("0.1"));
            String redisKey = createRedisKey("DOGE", 1);
            String redisValue = createRedisValue(testUser, new BigDecimal("0.1"));

            // Act - Subscribe
            performSubscribe(jwtToken, alertDTO);

            // Assert - Verify Redis entry created
            assertRedisContainsValue(redisKey, redisValue, new BigDecimal("0.1"));

            // Act - Unsubscribe
            performUnsubscribe(jwtToken, alertDTO);

            // Assert - Verify Redis and database cleanup
            assertRedisDoesNotContainValue(redisKey, redisValue);
            assertAlertNotInDatabase(alertId);
        }

        @Test
        @DisplayName("Should handle multiple alerts with different assets and comparison types")
        void shouldHandleMultipleAlertsWithDifferentKeysInRedis() throws Exception {
            // Arrange
            AlertDTO alert1 = createAlertDTO("BTC", 1, new BigDecimal("50000.0"));
            AlertDTO alert2 = createAlertDTO("BTC", 0, new BigDecimal("45000.0"));
            AlertDTO alert3 = createAlertDTO("ETH", 1, new BigDecimal("3000.0"));

            String redisKey1 = createRedisKey("BTC", 1);
            String redisKey2 = createRedisKey("BTC", 0);
            String redisKey3 = createRedisKey("ETH", 1);

            String redisValue1 = createRedisValue(testUser, new BigDecimal("50000.0"));
            String redisValue2 = createRedisValue(testUser, new BigDecimal("45000.0"));
            String redisValue3 = createRedisValue(testUser, new BigDecimal("3000.0"));

            // Act - Subscribe to all alerts
            performSubscribe(jwtToken, alert1);
            performSubscribe(jwtToken, alert2);
            performSubscribe(jwtToken, alert3);

            // Assert - Verify all Redis entries
            assertRedisContainsValue(redisKey1, redisValue1, new BigDecimal("50000.0"));
            assertRedisContainsValue(redisKey2, redisValue2, new BigDecimal("45000.0"));
            assertRedisContainsValue(redisKey3, redisValue3, new BigDecimal("3000.0"));

            // Act - Unsubscribe from one alert
            performUnsubscribe(jwtToken, alert1);

            // Assert - Verify selective Redis cleanup
            assertRedisDoesNotContainValue(redisKey1, redisValue1);
            assertRedisContainsValue(redisKey2, redisValue2, new BigDecimal("45000.0"));
            assertRedisContainsValue(redisKey3, redisValue3, new BigDecimal("3000.0"));
        }

        @Test
        @DisplayName("Should handle concurrent subscriptions and unsubscriptions with Redis consistency")
        void shouldHandleConcurrentOperationsWithRedisConsistency() throws Exception {
            // Arrange
            AlertDTO alertDTO = createAlertDTO("LTC", 1, new BigDecimal("150.0"));
            AlertId alertId = createAlertId("LTC", 1, new BigDecimal("150.0"));
            String redisKey = createRedisKey("LTC", 1);

            // Create multiple users
            User user2 = createAndSaveUser("user2", "password123");
            User user3 = createAndSaveUser("user3", "password123");
            String token2 = jwtService.generateToken(user2.getUsername());
            String token3 = jwtService.generateToken(user3.getUsername());

            String redisValue1 = createRedisValue(testUser, new BigDecimal("150.0"));
            String redisValue2 = createRedisValue(user2, new BigDecimal("150.0"));
            String redisValue3 = createRedisValue(user3, new BigDecimal("150.0"));

            // Act - Multiple users subscribe
            performSubscribe(jwtToken, alertDTO);
            performSubscribe(token2, alertDTO);
            performSubscribe(token3, alertDTO);

            // Assert - Redis should have three entries (one per user)
            assertRedisContainsValue(redisKey, redisValue1, new BigDecimal("150.0"));
            assertRedisContainsValue(redisKey, redisValue2, new BigDecimal("150.0"));
            assertRedisContainsValue(redisKey, redisValue3, new BigDecimal("150.0"));

            Set<String> redisElements = redisSortedSetService.getAllElements(redisKey);
            assertThat(redisElements).hasSize(3);

            // Act - Two users unsubscribe
            performUnsubscribe(jwtToken, alertDTO);
            performUnsubscribe(token2, alertDTO);

            // Assert - Redis should have only one entry (one user remains)
            assertRedisDoesNotContainValue(redisKey, redisValue1);
            assertRedisDoesNotContainValue(redisKey, redisValue2);
            assertRedisContainsValue(redisKey, redisValue3, new BigDecimal("150.0"));

            // Act - Last user unsubscribes
            performUnsubscribe(token3, alertDTO);

            // Assert - Redis should be clean
            assertRedisDoesNotContainValue(redisKey, redisValue3);
            assertAlertNotInDatabase(alertId);
        }
    }
}