import com.vanniktech.maven.publish.SonatypeHost

val ktorVersion = findProperty("ktor_version")

plugins {
    id("maven-publish")
    id("kotlin")
    id("com.vanniktech.maven.publish")
    kotlin("plugin.serialization") version "1.6.10"
}

dependencies {
    implementation(project(":conductor-ios"))
    implementation(project(":conductor-android-models"))

    api("com.michael-bull.kotlin-result:kotlin-result:1.1.14")
    api("dev.mobile:dadb:0.0.10")
    api("org.slf4j:slf4j-simple:1.7.36")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
}

plugins.withId("com.vanniktech.maven.publish") {
    mavenPublish {
        sonatypeHost = SonatypeHost.S01
    }
}