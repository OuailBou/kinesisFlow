package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.AlertDTO;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.listener.DlqListener;
import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.service.RedisSortedSetService;
import org.example.kinesisflow.service.RedisStringService;
import org.example.kinesisflow.websocket.NotifierWebSocketHandler;
import org.example.kinesisflow.websocket.CustomWebSocketClient;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.transaction.annotation.Transactional;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertNotificationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationIntegrationTest.class);

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
        registry.add("server.port", () -> "8081");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisSortedSetService redisSortedSetService;

    @Autowired
    private KafkaTemplate<String, cryptoEvent> kafkaTemplate;

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
    private BlockingQueue<String> receivedMessages;
    private CountDownLatch messageLatch;

    @BeforeEach
    void setUp() throws Exception {
        redisSortedSetService.deleteAll();
        createTestUser();
        jwtToken = authenticateAndGetToken();
        receivedMessages = new LinkedBlockingQueue<>();
        setupWebSocketConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webSocketClient != null && !webSocketClient.isClosed()) {
            webSocketClient.close();
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
        receivedMessages = new LinkedBlockingQueue<>();
        messageLatch = new CountDownLatch(1);

        webSocketClient = new CustomWebSocketClient(new URI("ws://localhost:8081/ws/notifications?token=" + jwtToken)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("WebSocket connected to server");
            }

            @Override
            public void onMessage(String message) {
                log.info("WebSocket received message: {}", message);
                receivedMessages.offer(message);
                messageLatch.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("WebSocket connection closed: {}", reason);
            }

            @Override
            public void onError(Exception ex) {
                log.error("WebSocket error: {}", ex.getMessage(), ex);
                messageLatch.countDown();
            }
        };

        boolean connected = webSocketClient.connectBlocking(10, TimeUnit.SECONDS);
        if (!connected) {
            throw new RuntimeException("Failed to connect to WebSocket");
        }
    }

    private void sendCryptoEvent(String asset, BigDecimal price) throws Exception {
        cryptoEvent event = new cryptoEvent(asset, price, Instant.now().toEpochMilli());
        mockMvc.perform(post(INGEST_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk());
    }

    private void waitForMessage(int timeoutSeconds) throws InterruptedException {
        messageLatch = new CountDownLatch(1);
        boolean messageReceived = messageLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Should have received a notification message within " + timeoutSeconds + " seconds");
    }

    private void waitForNoMessage(int timeoutSeconds) throws InterruptedException {
        String message = receivedMessages.poll(timeoutSeconds, TimeUnit.SECONDS);
        assertNull(message, "Should not have received any notification message");
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

        String gtKey = redisSortedSetService.createRuleIndexKey(TEST_ASSET, "1");
        Set<String> storedAlerts = redisSortedSetService.getAllElements(gtKey);
        assertFalse(storedAlerts.isEmpty(), "Alert should be stored in Redis");

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        Thread.sleep(2000);

        sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));
        waitForMessage(10);

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

        sendCryptoEvent(TEST_ASSET, new BigDecimal("52000"));
        Thread.sleep(2000);

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        waitForMessage(10);

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

        sendCryptoEvent("BTC", new BigDecimal("49000"));
        sendCryptoEvent("ETH", new BigDecimal("2900"));
        Thread.sleep(2000);

        sendCryptoEvent("BTC", new BigDecimal("51000"));
        sendCryptoEvent("ETH", new BigDecimal("3100"));

        waitForMessage(10);
        Thread.sleep(3000);
        int totalMessages = receivedMessages.size() + 1;
        assertTrue(totalMessages >= 2, "Should have received at least 2 notifications, got: " + totalMessages);

        verify(webSocketHandler, timeout(5000).atLeast(2))
                .sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(4)
    @DisplayName("No notification when price criteria not met")
    void testNoNotificationWhenCriteriaNotMet() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);

        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        Thread.sleep(2000);
        sendCryptoEvent(TEST_ASSET, new BigDecimal("49500"));
        Thread.sleep(3000);

        waitForNoMessage(3);
        verify(webSocketHandler, never()).sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(5)
    @DisplayName("Unsubscribe stops notifications")
    void testUnsubscribeStopsNotifications() throws Exception {
        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);

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

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        Thread.sleep(2000);
        sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));
        Thread.sleep(3000);

        waitForNoMessage(3);
        verify(webSocketHandler, never()).sendMessageToUser(eq(TEST_USERNAME), anyString());
    }

    @Test
    @Order(6)
    @DisplayName("Processing failure routes message to DLQ")
    void testProcessingFailureRoutesToDlq() {
        doThrow(new RuntimeException("Simulated Redis failure"))
                .when(redisStringService).get(anyString());

        try {
            cryptoEvent validEvent = new cryptoEvent("BTC", new BigDecimal("70000"), System.currentTimeMillis());
            dlqListener.clearMessages();

            kafkaTemplate.send("raw-market-data", validEvent.asset(), validEvent);

            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertEquals(1, dlqListener.getDlqRecords().size());
                    });
        } finally {
            reset(redisStringService);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Successful processing does not route to DLQ")
    void testSuccessfulProcessingDoesNotRouteToDelq() {
        cryptoEvent validEvent = new cryptoEvent("BTC", new BigDecimal("70000"), System.currentTimeMillis());
        dlqListener.clearMessages();

        kafkaTemplate.send("raw-market-data", validEvent.asset(), validEvent);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
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

        sendCryptoEvent(TEST_ASSET, new BigDecimal("48000"));
        Thread.sleep(2000);
        sendCryptoEvent(TEST_ASSET, new BigDecimal("52000"));

        waitForMessage(10);
        Thread.sleep(5000);
        int totalMessages = receivedMessages.size() + 1;
        assertTrue(totalMessages >= 3, "Should have received at least 3 notifications for concurrent alerts, got: " + totalMessages);

        verify(webSocketHandler, timeout(10000).atLeast(3))
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

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        Thread.sleep(2000);
        sendCryptoEvent(TEST_ASSET, TEST_PRICE);
        Thread.sleep(3000);

        String message = receivedMessages.poll(2, TimeUnit.SECONDS);
        assertNull(message, "EQUAL comparison should not trigger notifications");
    }

    @Test
    @Order(10)
    @DisplayName("Direct WebSocket connection and message handling")
    void testDirectWebSocketConnection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
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

        client.connectBlocking(10, TimeUnit.SECONDS);

        AlertDTO alertDTO = createAlertDTO(TEST_ASSET, TEST_PRICE, GREATER_THAN);
        mockMvc.perform(post(SUBSCRIBE_ENDPOINT)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alertDTO)))
                .andExpect(status().isCreated());

        sendCryptoEvent(TEST_ASSET, new BigDecimal("49000"));
        Thread.sleep(2000);
        sendCryptoEvent(TEST_ASSET, new BigDecimal("51000"));

        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);
        client.close();

        assertTrue(messageReceived, "Should have received a message via WebSocket");
        assertNotNull(receivedMsg[0], "Received message should not be null");
        assertTrue(receivedMsg[0].contains(TEST_ASSET), "Message should contain asset name");
    }
}