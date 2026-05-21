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
