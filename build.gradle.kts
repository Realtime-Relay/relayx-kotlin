plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "com.realtime.relay"
version = "v1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.nats:jnats:2.24.1")
    implementation("com.ensarsarajcic.kotlinx:serialization-msgpack:0.5.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    testImplementation(kotlin("test"))
}

tasks.test {
}
kotlin {
    jvmToolchain(22)
}