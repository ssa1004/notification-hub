// Spring Boot 진입점. main + 통합 config + Flyway 마이그레이션.
//
// Kotlin 마이그레이션 — main / @Configuration / readiness coordinator 모두 Kotlin.
// plugin.spring 은 @Configuration / @Component 의 open 처리, Spring Boot SpringApplication.run
// 은 KClass 의 .java 를 받는 형태로 호출.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // OpenAPI spec build-time export — generateOpenApiDocs 가 앱을 부팅한 뒤
    // /v3/api-docs 를 fetch 해 docs/openapi/notification-hub.yaml 로 떨어뜨린다.
    id("org.springdoc.openapi-gradle-plugin")
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

    // Spring Boot 가 Kotlin @ConfigurationProperties / data class 의 PreferredConstructor 를 호출할 때 reflect 필요.
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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

// OpenAPI spec export 설정 — ./gradlew :notification-bootstrap:generateOpenApiDocs.
// 플러그인이 bootRun 으로 앱을 띄우고 apiDocsUrl 을 fetch 해 outputFileName 으로 저장한다.
// 앱 부팅에 Postgres / Redis / Kafka 가 필요하므로 로컬 단독 실행보다는 CI 에서
// docker compose 와 함께 돌리는 것을 권장 (docs/openapi/README.md 참고).
openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.projectDirectory.dir("../docs/openapi"))
    outputFileName.set("notification-hub.yaml")
    waitTimeInSeconds.set(120)
}
