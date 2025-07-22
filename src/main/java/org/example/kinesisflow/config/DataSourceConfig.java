package org.example.kinesisflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import javax.sql.DataSource;
import java.io.IOException;

@Profile("aws")
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource rdsDataSource() throws IOException {
        String secret = getSecretFromAWS();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(secret);

        String url = String.format("jdbc:postgresql://%s:%s/%s",
                node.get("host").asText(),
                node.get("port").asText(),
                node.get("dbname").asText());
        String username = node.get("username").asText();
        String password = node.get("password").asText();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return dataSource;
    }

    private static String getSecretFromAWS() {
        String secretName = "rds!db-6874196d-0d92-4657-b977-d46618672fbc";
        Region region = Region.of("eu-west-3");

        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build();

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse getSecretValueResponse;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get secret from AWS Secrets Manager", e);
        }

        return getSecretValueResponse.secretString();
    }
}
