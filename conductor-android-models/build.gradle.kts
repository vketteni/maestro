val ktorVersion = findProperty("ktor_version")

plugins {
    id("kotlin")
    kotlin("plugin.serialization") version "1.6.10"
}

dependencies {
    implementation("io.ktor:ktor-serialization:$ktorVersion")
}
