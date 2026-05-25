// Inbound adapter — REST controller (springdoc) + Kafka consumer (vendor 콜백 / 사외 이벤트).
//
// Kotlin 마이그레이션 — controller / DTO / kafka consumer / security helper / exception handler 모두 Kotlin.
// plugin.spring 이 @RestController / @Component / @RestControllerAdvice 가 붙은 class 를 자동 open
// 처리해 CGLIB proxy 가 가능하게 한다 (@Transactional / @KafkaListener proxy 대상).
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":notification-application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI 문서
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // Kotlin null-safety 와 호환되는 Jackson module — Kotlin data class 의 non-null 필드를 인식해
    // Instant / Enum / record 역직렬화가 정상 동작한다.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Spring MVC 가 Kotlin controller 의 default 파라미터 / data class 를 reflective 하게 처리.
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    // Mockito Kotlin helpers — any() / whenever / verify 의 Kotlin friendly DSL.
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
