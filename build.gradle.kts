plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("com.github.PZDonny.DisplayEntityUtils:api:3.7.0")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
