package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
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

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisSortedSetService redisSortedSetService;

    // Test Data Constants
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_ASSET = "BTC";
    private static final BigDecimal TEST_PRICE = new BigDecimal("50000");

    // ComparisonType constants
    private static final int GREATER_THAN = 1;
    private static final int LESS_THAN = -1;
    private static final int EQUAL = 0;

    // API Endpoints
    private static final String SUBSCRIBE_ENDPOINT = "/api/alerts/subscribe";
    private static final String UNSUBSCRIBE_ENDPOINT = "/api/alerts/unsubscribe";
    private static final String USERS_ENDPOINT = "/auth/users";
    private static final String LOGIN_ENDPOINT = "/auth/login";

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        redisSortedSetService.deleteAll();
        createTestUser();
        jwtToken = authenticateAndGetToken();
    }

    // =========================== HAPPY PATH TESTS ===========================

    @Nested
    @DisplayName("Alert Subscription Tests")
    class AlertSubscriptionTests {

        @Test
        @DisplayName("Should successfully subscribe to alert and store in Redis")
        void shouldSubscribeToAlertAndStoreInRedis() throws Exception {
            // Given
            AlertDTO alertDTO = createValidAlertDTO();

            // When & Then
            performSuccessfulSubscription(alertDTO);
            verifyAlertStoredInRedis(alertDTO);
        }

        @Test
        @DisplayName("Should handle duplicate subscription gracefully")
        void shouldHandleDuplicateSubscriptionGracefully() throws Exception {
            // Given
            AlertDTO alertDTO = createValidAlertDTO();

            // When - First subscription
            performSuccessfulSubscription(alertDTO);

            // When - Second subscription (duplicate)
            performSuccessfulSubscription(alertDTO);

            // Then - Verify only one element in Redis
            verifyAlertStoredInRedis(alertDTO);
            String expectedKey = createExpectedRedisKey(alertDTO);
            Set<String> redisElements = redisSortedSetService.getAllElements(expectedKey);
            assertThat(redisElements).hasSize(1);
        }

        @Test
        @DisplayName("Should handle multiple alerts for same user")
        void shouldHandleMultipleAlertsForSameUser() throws Exception {
            // Given
            AlertDTO alert1 = createAlertDTO("BTC", GREATER_THAN, new BigDecimal("60000"));
            AlertDTO alert2 = createAlertDTO("BTC", LESS_THAN, new BigDecimal("40000"));
            AlertDTO alert3 = createAlertDTO("ETH", GREATER_THAN, new BigDecimal("4000"));

            // When
            performSuccessfulSubscription(alert1);
            performSuccessfulSubscription(alert2);
            performSuccessfulSubscription(alert3);

            // Then
            verifyAlertStoredInRedis(alert1);
            verifyAlertStoredInRedis(alert2);
            verifyAlertStoredInRedis(alert3);

            Set<String> allKeys = redisSortedSetService.getAllKeys();
            assertThat(allKeys).hasSize(3);
        }

        @Test
        @DisplayName("Should handle all comparison types")
        void shouldHandleAllComparisonTypes() throws Exception {
            // Given
            AlertDTO greaterThanAlert = createAlertDTO("BTC", GREATER_THAN, new BigDecimal("50000"));
            AlertDTO lessThanAlert = createAlertDTO("BTC", LESS_THAN, new BigDecimal("40000"));
            AlertDTO equalAlert = createAlertDTO("BTC", EQUAL, new BigDecimal("45000"));

            // When
            performSuccessfulSubscription(greaterThanAlert);
            performSuccessfulSubscription(lessThanAlert);
            performSuccessfulSubscription(equalAlert);

            // Then
            verifyAlertStoredInRedis(greaterThanAlert);
            verifyAlertStoredInRedis(lessThanAlert);
            verifyAlertStoredInRedis(equalAlert);

            Set<String> allKeys = redisSortedSetService.getAllKeys();
            assertThat(allKeys).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Alert Unsubscription Tests")
    class AlertUnsubscriptionTests {

        @Test
        @DisplayName("Should successfully unsubscribe from alert and remove from Redis")
        void shouldUnsubscribeFromAlertAndRemoveFromRedis() throws Exception {
            // Given
            AlertDTO alertDTO = createValidAlertDTO();
            performSuccessfulSubscription(alertDTO);
            verifyAlertStoredInRedis(alertDTO);

            // When
            performSuccessfulUnsubscription(alertDTO);

            // Then
            verifyAlertRemovedFromRedis(alertDTO);
        }

        @Test
        @DisplayName("Should return 404 when unsubscribing from non-existent alert")
        void shouldReturn404ForNonExistentAlert() throws Exception {
            // Given
            AlertDTO alertDTO = createValidAlertDTO();

            // When & Then
            mockMvc.perform(delete(UNSUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================== ERROR HANDLING TESTS ===========================

    @Nested
    @DisplayName("Authentication Error Tests")
    class AuthenticationErrorTests {

        @Test
        @DisplayName("Should return 401 for subscription without token")
        void shouldReturn401ForSubscriptionWithoutToken() throws Exception {
            AlertDTO alertDTO = createValidAlertDTO();

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for unsubscription without token")
        void shouldReturn401ForUnsubscriptionWithoutToken() throws Exception {
            AlertDTO alertDTO = createValidAlertDTO();

            mockMvc.perform(delete(UNSUBSCRIBE_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 for invalid token")
        void shouldReturn401ForInvalidToken() throws Exception {
            AlertDTO alertDTO = createValidAlertDTO();

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer invalid_token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 for bad credentials during login")
        void shouldReturn401ForBadCredentials() throws Exception {
            UserDTO invalidUserDTO = createUserDTO("wronguser", "wrongpassword");

            mockMvc.perform(post(LOGIN_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUserDTO)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid username or password"));
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should return 400 for invalid alert DTO - missing asset")
        void shouldReturn400ForMissingAsset() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(null, GREATER_THAN, TEST_PRICE);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.asset").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid alert DTO - blank asset")
        void shouldReturn400ForBlankAsset() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO("", GREATER_THAN, TEST_PRICE);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.asset").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid alert DTO - missing price")
        void shouldReturn400ForMissingPrice() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(TEST_ASSET, GREATER_THAN, null);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.price").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid alert DTO - negative price")
        void shouldReturn400ForNegativePrice() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(TEST_ASSET, GREATER_THAN, new BigDecimal("-100"));

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.price").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid alert DTO - zero price")
        void shouldReturn400ForZeroPrice() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(TEST_ASSET, GREATER_THAN, BigDecimal.ZERO);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.price").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid comparisonType - too high")
        void shouldReturn400ForInvalidComparisonTypeTooHigh() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(TEST_ASSET, 2, TEST_PRICE);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.comparisonType").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for invalid comparisonType - too low")
        void shouldReturn400ForInvalidComparisonTypeTooLow() throws Exception {
            AlertDTO invalidAlertDTO = createAlertDTO(TEST_ASSET, -2, TEST_PRICE);

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidAlertDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.comparisonType").exists()); // Field validation error
        }

        @Test
        @DisplayName("Should return 400 for malformed JSON")
        void shouldReturn400ForMalformedJson() throws Exception {
            String malformedJson = "{\"asset\": \"BTC\", \"price\": invalid_number}";

            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Malformed JSON request"))
                    .andExpect(jsonPath("$.details").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Should return 400 for invalid user DTO - missing username")
        void shouldReturn400ForMissingUsername() throws Exception {
            UserDTO invalidUserDTO = createUserDTO(null, TEST_PASSWORD);

            mockMvc.perform(post(USERS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUserDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.username").exists()); // Field validation error
        }
    }

    @Nested
    @DisplayName("Conflict Error Tests")
    class ConflictErrorTests {

        @Test
        @DisplayName("Should return 409 for duplicate user creation")
        void shouldReturn409ForDuplicateUser() throws Exception {
            UserDTO userDTO = createUserDTO("duplicateuser", TEST_PASSWORD);

            // First creation should succeed
            mockMvc.perform(post(USERS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userDTO)))
                    .andExpect(status().isCreated());

            // Second creation should fail with conflict
            mockMvc.perform(post(USERS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userDTO)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("User duplicateuser already exists"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================== REDIS VERIFICATION TESTS ===========================

    @Nested
    @DisplayName("Redis Integration Tests")
    class RedisIntegrationTests {

        @Test
        @DisplayName("Should verify Redis data structure integrity")
        void shouldVerifyRedisDataStructureIntegrity() throws Exception {
            // Given
            AlertDTO alertDTO = createAlertDTO("SOL", GREATER_THAN, new BigDecimal("100.50"));

            // When
            performSuccessfulSubscription(alertDTO);

            // Then
            String expectedKey = "SOL:1"; // GREATER_THAN = 1
            String expectedValue = TEST_USERNAME + ":100.5";

            Set<String> elements = redisSortedSetService.getAllElements(expectedKey);
            assertThat(elements).contains(expectedValue);

            Double score = redisSortedSetService.getScore(expectedKey, expectedValue);
            assertThat(score).isEqualTo(100.5);

            // Verify range queries work
            Set<String> rangeResults = redisSortedSetService.getRangeByScore(
                    expectedKey,
                    new BigDecimal("100"),
                    new BigDecimal("101"),
                    true,
                    true);
            assertThat(rangeResults).contains(expectedValue);
        }

        @Test
        @DisplayName("Should handle Redis cleanup properly")
        void shouldHandleRedisCleanupProperly() throws Exception {
            // Given
            AlertDTO alertDTO = createValidAlertDTO();
            performSuccessfulSubscription(alertDTO);
            verifyAlertStoredInRedis(alertDTO);

            // When
            performSuccessfulUnsubscription(alertDTO);

            // Then
            verifyAlertRemovedFromRedis(alertDTO);
        }

        @Test
        @DisplayName("Should handle different comparison types in Redis keys")
        void shouldHandleDifferentComparisonTypesInRedisKeys() throws Exception {
            // Given
            AlertDTO greaterThanAlert = createAlertDTO("BTC", GREATER_THAN, new BigDecimal("50000"));
            AlertDTO lessThanAlert = createAlertDTO("BTC", LESS_THAN, new BigDecimal("40000"));
            AlertDTO equalAlert = createAlertDTO("BTC", EQUAL, new BigDecimal("45000"));

            // When
            performSuccessfulSubscription(greaterThanAlert);
            performSuccessfulSubscription(lessThanAlert);
            performSuccessfulSubscription(equalAlert);

            // Then
            verifyAlertStoredInRedis(greaterThanAlert);
            verifyAlertStoredInRedis(lessThanAlert);
            verifyAlertStoredInRedis(equalAlert);

            // Verify different keys are created
            Set<String> allKeys = redisSortedSetService.getAllKeys();
            assertThat(allKeys).containsExactlyInAnyOrder("BTC:1", "BTC:-1", "BTC:0");
        }
    }

    // =========================== HELPER METHODS ===========================

    private void createTestUser() throws Exception {
        UserDTO userDTO = createUserDTO(TEST_USERNAME, TEST_PASSWORD);

        mockMvc.perform(post(USERS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created"))
                .andExpect(jsonPath("$.username").value(TEST_USERNAME));
    }

    private String authenticateAndGetToken() throws Exception {
        UserDTO loginDTO = createUserDTO(TEST_USERNAME, TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, String> tokenResponse = objectMapper.readValue(response, Map.class);
        return tokenResponse.get("token");
    }

    private void performSuccessfulSubscription(AlertDTO alertDTO) throws Exception {
        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Alert created"))
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.alert.asset").value(alertDTO.getAsset()))
                .andExpect(jsonPath("$.alert.comparisonType").value(alertDTO.getComparisonType()))
                .andExpect(jsonPath("$.alert.price").value(alertDTO.getPrice().doubleValue()));
    }

    private void performSuccessfulUnsubscription(AlertDTO alertDTO) throws Exception {
        mockMvc.perform(delete(UNSUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have unsubscribed from alert succesfully"))
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.alert.asset").value(alertDTO.getAsset()));
    }

    private void verifyAlertStoredInRedis(AlertDTO alertDTO) {
        String expectedKey = createExpectedRedisKey(alertDTO);
        String expectedValue = createExpectedRedisValue(alertDTO);

        Set<String> redisElements = redisSortedSetService.getAllElements(expectedKey);
        assertThat(redisElements).isNotEmpty();
        assertThat(redisElements).contains(expectedValue);

        Double score = redisSortedSetService.getScore(expectedKey, expectedValue);
        assertThat(score).isEqualTo(alertDTO.getPrice().doubleValue());
    }

    private void verifyAlertRemovedFromRedis(AlertDTO alertDTO) {
        String expectedKey = createExpectedRedisKey(alertDTO);
        Set<String> redisElements = redisSortedSetService.getAllElements(expectedKey);
        assertThat(redisElements).isEmpty();
    }

    private String createExpectedRedisKey(AlertDTO alertDTO) {
        return redisSortedSetService.createRuleIndexKey(
                alertDTO.getAsset(),
                String.valueOf(alertDTO.getComparisonType()));
    }

    private String createExpectedRedisValue(AlertDTO alertDTO) {
        return redisSortedSetService.createRuleIndexValue(
                createTestUser2(),
                alertDTO.getPrice());
    }

    private AlertDTO createValidAlertDTO() {
        return createAlertDTO(TEST_ASSET, GREATER_THAN, TEST_PRICE);
    }

    private AlertDTO createAlertDTO(String asset, int comparisonType, BigDecimal price) {
        AlertDTO alertDTO = new AlertDTO();
        alertDTO.setAsset(asset);
        alertDTO.setComparisonType(comparisonType);
        alertDTO.setPrice(price);
        return alertDTO;
    }

    private UserDTO createUserDTO(String username, String password) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        userDTO.setPassword(password);
        return userDTO;
    }

    private User createTestUser2() {
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setPassword(TEST_PASSWORD);
        return user;
    }
}