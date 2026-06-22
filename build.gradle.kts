// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
plugins {
    java
    // Kotlin 버전은 여기서 한 곳에 고정. 실제 적용은 Kotlin 으로 마이그레이션된 모듈만.
    // plugin.spring 은 application / adapter-in / adapter-out 에서 @Service / @Repository /
    // @Component / @RestControllerAdvice 의 자동 open, plugin.jpa 는 @Entity no-arg constructor 합성.
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    kotlin("plugin.jpa") version "2.4.0" apply false
    id("org.springframework.boot") version "4.1.0" apply false
    // 루트에도 적용한다(apply false 아님). Kover 합산 리포트가 만드는 koverExternalArtifacts
    // 구성은 루트 프로젝트에서 해석(resolve)되는데, 코드 모듈들이 버전 없이 선언한 Spring 의존성
    // (예: org.springframework:spring-tx)의 버전은 spring-boot BOM 이 공급한다. 루트에 BOM 이
    // 없으면 "Could not find org.springframework:spring-tx:" (빈 버전)으로 해석에 실패한다.
    id("io.spring.dependency-management") version "1.1.7"
    // OpenAPI spec build-time export — 실제 적용은 bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // Kotlin-native 커버리지 — 루트에 적용하고 코드 모듈을 kover(...) 의존으로 묶어
    // 멀티모듈 합산 리포트(XML/HTML)를 만든다. JaCoCo 대비 Kotlin inline/coroutine 처리에 정확.
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

allprojects {
    group = "com.example.notification"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

// 루트 프로젝트의 BOM 임포트 — Kover 의 koverExternalArtifacts 가 루트에서 코드 모듈의
// 버전 없는 Spring 의존성을 해석할 수 있도록, 서브프로젝트와 동일한 spring-boot BOM 을
// 루트에도 import 한다. (subprojects 블록은 서브프로젝트에만 적용되므로 루트에는 별도 필요)
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
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
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
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

// ── Kover 플러그인을 합산 대상 코드 모듈에 적용 ──────────────────────────────
// 합산 리포트가 실제 커버리지를 담으려면, kover(project(...)) 로 묶이는 각 모듈이 자신의
// test 태스크를 Kover 로 계측해 바이너리 리포트(koverArtifact)를 제공해야 한다. 모듈이 Kover
// 플러그인을 적용하지 않으면 해당 variant 가 없어 루트 합산이 컴파일 클래스패스만 끌어오고
// (koverExternalArtifacts 해석 실패의 원인), 리포트는 커버리지 0 으로 비게 된다.
// e2e-tests 는 프로덕션 코드가 없는 Testcontainers 통합 테스트 전용 모듈이라 합산/계측 대상에서 제외.
configure(subprojects.filter { it.name != "e2e-tests" }) {
    apply(plugin = "org.jetbrains.kotlinx.kover")
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
