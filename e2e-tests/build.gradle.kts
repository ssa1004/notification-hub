// 통합 시나리오 — Postgres + Redis + Kafka Testcontainers 위에서 전체 흐름 확인.
plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":notification-bootstrap"))
    testImplementation(project(":notification-application"))
    testImplementation(project(":notification-domain"))

    testImplementation(project(":notification-adapter-out"))
    testImplementation(project(":notification-adapter-in"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testRuntimeOnly("org.postgresql:postgresql")
}
