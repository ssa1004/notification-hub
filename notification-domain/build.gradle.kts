// 순수 도메인. Spring 의존성 0. JPA 어노테이션도 0. (헥사고날 핵심)
// jakarta.validation 만 허용 — Bean Validation 어노테이션은 표준이고 프레임워크 비의존.
//
// Java → Kotlin 마이그레이션 완료 모듈. Java caller (application / adapter) 와의 ABI 호환을
// 위해 @JvmRecord / @JvmStatic / @get:JvmName 등을 사용 — 자세한 규칙은 각 파일 주석 참조.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api("jakarta.validation:jakarta.validation-api")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    // Java toolchain (21) 과 동일한 JVM 타깃. @JvmRecord 는 16+ 필요.
    jvmToolchain(21)
}
