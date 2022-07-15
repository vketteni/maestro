import com.vanniktech.maven.publish.SonatypeHost.*

val ktorVersion = findProperty("ktor_version")

plugins {
    id("maven-publish")
    id("kotlin")
    id("com.vanniktech.maven.publish")
    kotlin("plugin.serialization") version "1.6.10"
}

dependencies {
    implementation("io.ktor:ktor-serialization:$ktorVersion")
}

plugins.withId("com.vanniktech.maven.publish") {
    mavenPublish {
        sonatypeHost = S01
    }
}
