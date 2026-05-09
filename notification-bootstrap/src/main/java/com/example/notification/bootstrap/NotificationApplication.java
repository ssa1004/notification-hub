package com.example.notification.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.notification")
@EntityScan(basePackages = "com.example.notification.adapter.out.persistence.entity")
@EnableJpaRepositories(basePackages = "com.example.notification.adapter.out.persistence.repository")
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
