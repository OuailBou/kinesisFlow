package org.example.kinesisflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;


import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")  // SOLO test
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("User Registration (/auth/users)")
    class UserRegistrationTests {

        @Test
        @DisplayName("Should register a new user successfully")
        void shouldRegisterNewUser() throws Exception {
            // Arrange
            String username = "testuser";
            String password = "password123";
            UserDTO userDTO = new UserDTO(username, password);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act
            MvcResult result = mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("User created"))
                    .andExpect(jsonPath("$.username").value(username))
                    .andReturn();

            // Assert
            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).isNotNull().isNotEmpty();

            User savedUser = userRepository.findByUsername(username).orElse(null);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getUsername()).isEqualTo(username);
            assertThat(passwordEncoder.matches(password, savedUser.getPassword())).isTrue();
        }

        @Test
        @DisplayName("Should return 400 when registering user with invalid data")
        void shouldReturnBadRequestForInvalidUserData() throws Exception {
            // Arrange
            String invalidJson = "{\"username\": \"\", \"password\": \"\"}";

            // Act & Assert
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.username").exists())
                    .andExpect(jsonPath("$.password").exists());

            assertThat(userRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when registering user with missing fields")
        void shouldReturnBadRequestForMissingFields() throws Exception {
            // Arrange
            String invalidJson = "{\"username\": \"testuser\"}";

            // Act & Assert
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.password").exists());

            assertThat(userRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Should return 409 when trying to register duplicate user")
        void shouldReturnConflictForDuplicateUser() throws Exception {
            // Arrange
            String username = "testuser";
            String password = "password123";

            User existingUser = new User();
            existingUser.setUsername(username);
            existingUser.setPassword(passwordEncoder.encode(password));
            userRepository.save(existingUser);

            UserDTO userDTO = new UserDTO(username, password);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act & Assert
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("User " + username + " already exists"));

            assertThat(userRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("Should return 400 when request body is malformed JSON")
        void shouldReturnBadRequestForMalformedJson() throws Exception {
            // Arrange
            String malformedJson = "{\"username\": \"testuser\", \"password\":}";

            // Act & Assert
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("User Authentication (/auth/login)")
    class UserAuthenticationTests {

        @Test
        @DisplayName("Should authenticate user and return valid JWT token")
        void shouldAuthenticateAndReturnToken() throws Exception {
            // Arrange
            String username = "testuser";
            String plainPassword = "password123";

            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(plainPassword));
            userRepository.save(user);

            UserDTO userDTO = new UserDTO(username, plainPassword);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act
            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn();

            // Assert
            String responseBody = result.getResponse().getContentAsString();
            Map<String, String> response = objectMapper.readValue(responseBody, Map.class);
            String token = response.get("token");

            assertThat(token)
                    .isNotNull()
                    .isNotEmpty()
                    .matches("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");
        }

        @Test
        @DisplayName("Should return 401 when authenticating with wrong password")
        void shouldReturnUnauthorizedWithWrongPassword() throws Exception {
            // Arrange
            String username = "testuser";
            String correctPassword = "password123";
            String wrongPassword = "wrongpassword";

            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(correctPassword));
            userRepository.save(user);

            UserDTO userDTO = new UserDTO(username, wrongPassword);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Invalid username or password"));
        }

        @Test
        @DisplayName("Should return 401 when authenticating non-existent user")
        void shouldReturnUnauthorizedForNonExistentUser() throws Exception {
            // Arrange
            String nonExistentUsername = "nonexistent";
            String password = "password123";

            UserDTO userDTO = new UserDTO(nonExistentUsername, password);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Invalid username or password"));
        }

        @Test
        @DisplayName("Should return 400 when authenticating with empty credentials")
        void shouldReturnBadRequestForEmptyCredentials() throws Exception {
            // Arrange
            String invalidJson = "{\"username\": \"\", \"password\": \"\"}";

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.username").exists())
                    .andExpect(jsonPath("$.password").exists());
        }

        @Test
        @DisplayName("Should return 400 when authenticating with missing fields")
        void shouldReturnBadRequestForMissingFields() throws Exception {
            // Arrange
            String invalidJson = "{\"username\": \"testuser\"}";

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.password").exists());
        }

        @Test
        @DisplayName("Should return 400 when authenticating with malformed JSON")
        void shouldReturnBadRequestForMalformedJson() throws Exception {
            // Arrange
            String malformedJson = "{\"username\": \"testuser\", \"password\":}";

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when request body is empty")
        void shouldReturnBadRequestForEmptyBody() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Complete User Journey")
    class CompleteUserJourneyTests {

        @Test
        @DisplayName("Should complete full user lifecycle: register → authenticate")
        void shouldCompleteFullUserLifecycle() throws Exception {
            // Arrange
            String username = "journeyuser";
            String password = "password123";
            UserDTO userDTO = new UserDTO(username, password);
            String requestJson = objectMapper.writeValueAsString(userDTO);

            // Act - Register user
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("User created"))
                    .andExpect(jsonPath("$.username").value(username));

            // Act - Authenticate user
            MvcResult authResult = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn();

            // Assert
            String responseBody = authResult.getResponse().getContentAsString();
            Map<String, String> response = objectMapper.readValue(responseBody, Map.class);
            String token = response.get("token");

            assertThat(token)
                    .isNotNull()
                    .isNotEmpty()
                    .matches("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");

            User savedUser = userRepository.findByUsername(username).orElse(null);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("Should prevent authentication after failed registration")
        void shouldPreventAuthenticationAfterFailedRegistration() throws Exception {
            // Arrange
            String invalidJson = "{\"username\": \"\", \"password\": \"\"}";
            String validAuthJson = "{\"username\": \"testuser\", \"password\": \"password123\"}";

            // Act - Try to register with invalid data
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            // Act - Try to authenticate user that wasn't created
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAuthJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Invalid username or password"));

            // Assert
            assertThat(userRepository.findAll()).isEmpty();
        }
    }


}