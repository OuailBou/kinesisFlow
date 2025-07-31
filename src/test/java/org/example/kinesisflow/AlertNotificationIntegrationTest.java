package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.record.CryptoEvent;
import org.example.kinesisflow.service.DlqListener;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.example.kinesisflow.service.RedisStringService;
import org.example.kinesisflow.websocket.NotifierWebSocketHandler;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.management.ThreadDumpEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.java_websocket.handshake.ServerHandshake;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertNotificationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationIntegrationTest.class);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0");

    @Autowired
    private ThreadDumpEndpoint threadDumpEndpoint;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("server.port", () -> "8081");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisSortedSetService redisSortedSetService;

    @Autowired
    private KafkaTemplate<String, CryptoEvent> kafkaTemplate;

    @SpyBean
    private NotifierWebSocketHandler webSocketHandler;

    @Autowired
    public DlqListener dlqListener;

    @SpyBean
    private RedisStringService redisStringService;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_ASSET = "BTC";
    private static final BigDecimal TEST_PRICE = new BigDecimal("50000");

    private static final int GREATER_THAN = 1;
    private static final int LESS_THAN = -1;
    private static final int EQUAL = 0;

    private static final String SUBSCRIBE_ENDPOINT = "/api/alerts/subscribe";
    private static final String UNSUBSCRIBE_ENDPOINT = "/api/alerts/unsubscribe";
    private static final String USERS_ENDPOINT = "/auth/users";
    private static final String LOGIN_ENDPOINT = "/auth/login";
    private static final String INGEST_ENDPOINT = "/ingest";

    private String jwtToken;
    private CustomWebSocketClient webSocketClient;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private final AtomicInteger messageCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        // Close any existing WebSocket connection
        if (webSocketClient != null && !webSocketClient.isClosed()) {
            try {
                webSocketClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Reset all components to clean state
        reset(webSocketHandler, redisStringService);
        redisSortedSetService.deleteAll();
        receivedMessages.clear();
        messageCount.set(0);

        if (dlqListener != null) {
            dlqListener.clearMessages();
        }

        // Setup authenticated user and WebSocket connection
        createTestUser();
        jwtToken = authenticateAndGetToken();
        setupWebSocketConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean WebSocket connection
        if (webSocketClient != null && !webSocketClient.isClosed()) {
            try {
                webSocketClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Clean all state for next test
        redisSortedSetService.deleteAll();
        receivedMessages.clear();
        messageCount.set(0);
        reset(webSocketHandler, redisStringService);

        if (dlqListener != null) {
            dlqListener.clearMessages();
        }
    }

    private void createTestUser() throws Exception {
        UserDTO userDTO = createUserDTO(TEST_USERNAME, TEST_PASSWORD);
        mockMvc.perform(post(USERS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isCreated());
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

    private UserDTO createUserDTO(String username, String password) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        userDTO.setPassword(password);
        return userDTO;
    }

    private AlertDTO createAlertDTO(String asset, BigDecimal price, int comparisonType) {
        AlertDTO alertDTO = new AlertDTO();
        alertDTO.setAsset(asset);
        alertDTO.setPrice(price);
        alertDTO.setComparisonType(comparisonType);
        return alertDTO;
    }

    private void setupWebSocketConnection() throws Exception {
        webSocketClient = new CustomWebSocketClient(new URI("ws://localhost:8081/ws/notifications?token=" + jwtToken)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("WebSocket connected to server");
            }

            @Override
            public void onMessage(String message) {
                log.info("WebSocket received message: {}", message);
                receivedMessages.offer(message);
                int count = messageCount.incrementAndGet();
                log.info("Message count now: {}", count);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("WebSocket connection closed: {} (code: {})", reason, code);
            }

            @Override
            public void onError(Exception ex) {
                log.error("WebSocket error: {}", ex.getMessage(), ex);
            }
        };

        boolean connected = webSocketClient.connectBlocking(10, TimeUnit.SECONDS);
        if (!connected) {
            throw new RuntimeException("Failed to connect to WebSocket");
        }

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> webSocketClient.isOpen());

        log.info("WebSocket connection established and ready");
    }

    private void sendCryptoEvent(String asset, BigDecimal price) throws Exception {
        CryptoEvent event = new CryptoEvent(asset, price, Instant.now().toEpochMilli());
        log.info("Sending crypto event: {} at price {}", asset, price);
        mockMvc.perform(post(INGEST_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk());
    }

    private void waitForMessages(int expectedCount, int timeoutSeconds) {
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> messageCount.get() >= expectedCount);
    }

    @Test
    @Order(1)
    @DisplayName("Subscribe to alert and receive notification on price increase")
    void testAlertNotificationOnPriceIncrease() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Alert created"))
                .andExpect(jsonPath("$.username").value(TEST_USERNAME));

        // Verify alert is stored in Redis
        String gtKey = redisSortedSetService.createRuleIndexKey(TEST_ASSET, "1");
        await().atMost(5, TimeUnit.SECONDS).until(() -> !redisSortedSetService.getAllElements(gtKey).isEmpty());

        // Send price below threshold (no trigger), then above threshold (should trigger)
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));

        waitForMessages(1, 10);

        verify(webSocketHandler, timeout(5000).atLeastOnce())
                .sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(2)
    @DisplayName("Subscribe to alert and receive notification on price decrease")
    void testAlertNotificationOnPriceDecrease() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, LESS_THAN);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        // Send price above threshold first, then below (should trigger on decrease)
        sendCryptoEvent(TEST_ASSET, new BigDecimal("52000"));
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));

        waitForMessages(1, 10);

        verify(webSocketHandler, timeout(5000).atLeastOnce())
                .sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(3)
    @DisplayName("Multiple alerts for different assets")
    void testMultipleAlertsForDifferentAssets() throws Exception {
        AlertDTO btcAlert = createAlertDTO("BTC", new BigDecimal("50000"), GREATER_THAN);
        AlertDTO ethAlert = createAlertDTO("ETH", new BigDecimal("3000"), GREATER_THAN);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(btcAlert)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ethAlert)))
                .andExpect(status().isCreated());

        // Send prices below thresholds first, then above both thresholds
        sendCryptoEvent("BTC", new BigDecimal("49000"));
        sendCryptoEvent("ETH", new BigDecimal("2900"));
        sendCryptoEvent("BTC", new BigDecimal("51000"));
        sendCryptoEvent("ETH", new BigDecimal("3100"));

        waitForMessages(2, 15);

        assertEquals(2, messageCount.get(), "Should have received exactly 2 notifications");
        verify(webSocketHandler, timeout(10000).times(2))
                .sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(4)
    @DisplayName("No notification when price criteria not met")
    void testNoNotificationWhenCriteriaNotMet() throws Exception {
        assertEquals(0, messageCount.get(), "Should start with zero messages");

        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        // Verify alert is stored before testing
        String gtKey = redisSortedSetService.createRuleIndexKey(TEST_ASSET, "1");
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !redisSortedSetService.getAllElements(gtKey).isEmpty());

        // Send prices that don't meet criteria (both below 50000)
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49500"));

        // Wait for processing to complete
        Thread.sleep(2000);

        assertEquals(0, messageCount.get(), "Should not have received any messages");
        verify(webSocketHandler, never()).sendMessageToUser(eq(TEST_USERNAME), anyString());

        log.info("Test completed - Final message count: {}", messageCount.get());
    }

    @Test
    @Order(5)
    @DisplayName("Unsubscribe stops notifications")
    void testUnsubscribeStopsNotifications() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);

        // Subscribe then immediately unsubscribe
        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(UNSUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have unsubscribed from alert succesfully"));

        // Send events that would normally trigger alerts
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));

        Thread.sleep(2000);

        assertEquals(0, messageCount.get(), "Should not have received any messages after unsubscribe");
        verify(webSocketHandler, never()).sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(6)
    @DisplayName("Processing failure routes message to DLQ")
    void testProcessingFailureRoutesToDlq() {
        // Simulate Redis failure to trigger DLQ routing
        doThrow(new RuntimeException("Simulated Redis failure"))
                .when(redisStringService).get(anyString());

        try {
            CryptoEvent validEvent = new CryptoEvent("BTC", new BigDecimal("70000"), System.currentTimeMillis());
            dlqListener.clearMessages();

            kafkaTemplate.send("raw-market-data", validEvent.asset(), validEvent);

            await().atMost(15, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        assertEquals(1, dlqListener.getDlqRecords().size(), "Message should be in DLQ");
                    });
        } finally {
            reset(redisStringService);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Successful processing does not route to DLQ")
    void testSuccessfulProcessingDoesNotRouteToDelq() {
        CryptoEvent validEvent = new CryptoEvent("BTC", new BigDecimal("70000"), System.currentTimeMillis());
        dlqListener.clearMessages();

        kafkaTemplate.send("raw-market-data", validEvent.asset(), validEvent);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertTrue(dlqListener.getDlqRecords().isEmpty(), "DLQ should be empty since processing succeeded");
                });
    }

    @Test
    @Order(8)
    @DisplayName("Concurrent alert processing")
    void testConcurrentAlertProcessing() throws Exception {
        List<AlertDTO> alerts = Arrays.asList(
                createAlertDTO(TEST_ASSET, new BigDecimal("49000"), GREATER_THAN),
                createAlertDTO(TEST_ASSET, new BigDecimal("50000"), GREATER_THAN),
                createAlertDTO(TEST_ASSET, new BigDecimal("51000"), GREATER_THAN)
        );

        for (AlertDTO alert : alerts) {
            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alert)))
                    .andExpect(status().isCreated());
        }

        // Wait for all alerts to be stored in Redis
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    String gtKey = redisSortedSetService.createRuleIndexKey(TEST_ASSET, "1");
                    return redisSortedSetService.getAllElements(gtKey).size() >= 3;
                });

        // Send price below all thresholds, then above all thresholds
        sendCryptoEvent(TEST_ASSET, new BigDecimal("48000"));
        sendCryptoEvent(TEST_ASSET, new BigDecimal("52000"));

        waitForMessages(1, 15);

        assertEquals(1, messageCount.get(), "Should have received exactly 1 notification for concurrent alerts");
        verify(webSocketHandler, timeout(15000).times(1))
                .sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(9)
    @DisplayName("Exact price match alert handling")
    void testExactPriceMatchAlert() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, EQUAL);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        // Test EQUAL comparison (should not trigger notifications)
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        sendCryptoEvent(TEST_ASSET, TEST_PRICE);

        Thread.sleep(2000);

        assertEquals(0, messageCount.get(), "EQUAL comparison should not trigger notifications");
    }

    @Test
    @Order(10)
    @DisplayName("Direct WebSocket connection and message handling")
    void testDirectWebSocketConnection() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] receivedMsg = new String[1];

        CustomWebSocketClient client = new CustomWebSocketClient(new URI("ws://localhost:8081/ws/notifications?token=" + jwtToken)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("Direct test WebSocket connected");
            }

            @Override
            public void onMessage(String message) {
                log.info("Direct test received message: {}", message);
                receivedMsg[0] = message;
                latch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("Direct test WebSocket closed: {}", reason);
            }

            @Override
            public void onError(Exception ex) {
                log.error("Direct test WebSocket error", ex);
                latch.countDown();
            }
        };

        assertTrue(client.connectBlocking(10, TimeUnit.SECONDS), "Should connect to WebSocket");

        try {
            AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);
            mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(alertDTO)))
                    .andExpect(status().isCreated());

            // Test direct WebSocket message delivery
            sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
            sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));

            boolean messageReceived = latch.await(10, TimeUnit.SECONDS);
            assertTrue(messageReceived, "Should have received a message via WebSocket");
            assertNotNull(receivedMsg[0], "Received message should not be null");
            assertTrue(receivedMsg[0].contains(TEST_ASSET), "Message should contain asset name");
        } finally {
            client.closeBlocking();
        }
    }
}