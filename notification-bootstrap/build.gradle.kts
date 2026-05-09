// Spring Boot 진입점. main + 통합 config + Flyway 마이그레이션.
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":notification-domain"))
    implementation(project(":notification-application"))
    implementation(project(":notification-adapter-in"))
    implementation(project(":notification-adapter-out"))

    // Bootstrap 자체에서 사용하는 starter
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Actuator + Prometheus
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // K8s readiness 신호 — Redis ping / Kafka cluster id 호출용. adapter-out 가 이미 transitively
    // 가져오나 헥사고날 의존 방향상 bootstrap 이 직접 의존을 명시.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named("bootJar") {
    enabled = true
}

// e2e-tests 가 NotificationApplication 클래스를 import 할 수 있도록 plain jar 도 활성화.
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
}
