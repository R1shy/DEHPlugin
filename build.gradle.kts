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
    implementation("net.dv8tion:JDA:6.5.0") {
        isTransitive = true
    }
    //compileOnly("com.github.PZDonny.DisplayEntityUtils:api:3.7.0")
    compileOnly(files("libs/displayentityutils-3.7.0.jar"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    relocate("net.dv8tion.jda", "net.rishy.dehplugin.libs.jda")
    mergeServiceFiles()
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
