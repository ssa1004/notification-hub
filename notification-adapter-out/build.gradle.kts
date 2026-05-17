// Outbound adapter — Persistence (JPA + Flyway), Redis, Kafka producer + Outbox relay,
// vendor mock client (FCM / SES / Twilio / Kakao AlimTalk). 외부 vendor SDK 는 의존성 X.
//
// Kotlin 마이그레이션 — entity / repository / mapper / adapter / vendor client 모두 Kotlin.
// plugin.spring 은 @Repository / @Component / @Configuration 의 open 처리, plugin.jpa 는
// @Entity 의 no-arg constructor 합성을 담당.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":notification-application"))

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Cache / KV
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Messaging
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // Kotlin null-safety 와 호환되는 Jackson module — Kotlin data class 의 nullability 인식.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Resilience (vendor 호출 retry / circuit breaker)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Mustache (template engine)
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")

    // Tracing / Metrics
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-core")

    // Spring Data JPA 가 Kotlin entity 의 PreferredConstructorDiscoverer 를 호출할 때 reflect 필요.
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
    // Mockito Kotlin helpers — any() / whenever / verify 의 Kotlin friendly DSL.
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
