// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
plugins {
    java
    // Kotlin 버전은 여기서 한 곳에 고정. 실제 적용은 Kotlin 으로 마이그레이션된 모듈만.
    // plugin.spring 은 application / adapter-in / adapter-out 에서 @Service / @Repository /
    // @Component / @RestControllerAdvice 의 자동 open, plugin.jpa 는 @Entity no-arg constructor 합성.
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // OpenAPI spec build-time export — 실제 적용은 bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // Kotlin-native 커버리지 — 루트에 적용하고 코드 모듈을 kover(...) 의존으로 묶어
    // 멀티모듈 합산 리포트(XML/HTML)를 만든다. JaCoCo 대비 Kotlin inline/coroutine 처리에 정확.
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "com.example.notification"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.13")
        }
    }

    dependencies {
        // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing"))
        options.encoding = "UTF-8"
    }
}

// ── 커버리지 합산 (Kover) ───────────────────────────────────────────────────
// 루트에서 코드 모듈을 kover 의존으로 묶어, 모듈별 단위 테스트 결과를 하나의 리포트로 합산한다.
// e2e-tests 는 프로덕션 코드가 없는 Testcontainers 통합 테스트 전용 모듈이라 합산 대상에서 제외.
dependencies {
    kover(project(":notification-domain"))
    kover(project(":notification-application"))
    kover(project(":notification-adapter-in"))
    kover(project(":notification-adapter-out"))
    kover(project(":notification-bootstrap"))
}

kover {
    reports {
        // bootstrap 진입점 / 설정 / JPA 엔티티(getter/setter)는 단위 테스트 대상이 아니므로 제외해
        // 비즈니스 로직 커버리지를 왜곡하지 않게 한다.
        filters {
            excludes {
                classes(
                    "com.example.notification.bootstrap.NotificationApplication*",
                    "*.config.*",
                    "*.persistence.entity.*",
                )
            }
        }
        total {
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
        }
    }
}
