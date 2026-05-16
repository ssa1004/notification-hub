// Use Cases + Ports. Spring 의존성은 stereotype + tx 만 (@Service, @Transactional).
// 외부 라이브러리 (DB 드라이버, Kafka, Redis) 직접 의존 금지 — 모두 Port 인터페이스로.
//
// application 모듈도 Kotlin 으로 작성. Spring AOP (@Transactional) 가 proxy 를 만들 수
// 있도록 plugin.spring 으로 @Service 클래스를 자동 open 처리한다.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring") version "1.9.25"
}

dependencies {
    api(project(":notification-domain"))
    api("org.springframework:spring-context")        // @Service, @Component
    api("org.springframework:spring-tx")              // @Transactional
    api("org.slf4j:slf4j-api")                       // Lombok @Slf4j

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // 인터페이스 default 메서드를 Java 측에 그대로 노출 (-Xjvm-default=all).
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
