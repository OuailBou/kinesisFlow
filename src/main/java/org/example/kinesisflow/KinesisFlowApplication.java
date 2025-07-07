package org.example.kinesisflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableRetry
@SpringBootApplication
//@EnableScheduling
@EnableKafka
public class KinesisFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(KinesisFlowApplication.class, args);
    }

}
