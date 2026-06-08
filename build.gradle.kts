plugins {
    kotlin("jvm") version "1.9.23"
}

repositories {
    mavenCentral()
}

dependencies {
    // Detekt API and Kotlin stdlib are only needed to compile the rules.
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.6")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")

    // Expose Kotlin compiler PSI to Detekt at runtime.
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.23")
}

