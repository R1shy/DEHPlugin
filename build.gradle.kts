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
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

sourceSets {
    create("dev") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

tasks.register<JavaExec>("devServer") {
    group = "development"
    description = "Starts the Paper dev server and hot-reloads the plugin into its plugins/ dir on every src change."
    dependsOn(tasks.named("devClasses"))
    classpath = sourceSets["dev"].runtimeClasspath
    mainClass = "net.rishy.dehplugin.dev.DevServer"
    val serverDir = providers.gradleProperty("devServer")
        .filter { it.isNotBlank() }
        .orElse(providers.environmentVariable("DEH_SERVER"))
        .filter { it.isNotBlank() }
    val startCommand = providers.gradleProperty("devStartCommand")
        .filter { it.isNotBlank() }
        .orElse(providers.environmentVariable("DEH_START_COMMAND"))
        .orNull
    if (serverDir.isPresent) {
        args(serverDir.get(), project.projectDir, startCommand ?: "")
    } else {
        doFirst {
            throw GradleException("devServer: no dev server configured. " +
                "Set devServer in gradle.properties or the DEH_SERVER env var.")
        }
    }
    standardInput = System.`in`
}

tasks.register<Copy>("deployDevServer") {
    group = "development"
    description = "Builds the plugin jar and copies it into a dev server's plugins/ folder (dev-only hot reload)."
    val serverDir = providers.gradleProperty("devServer")
        .filter { it.isNotBlank() }
        .orElse(providers.environmentVariable("DEH_SERVER"))
        .filter { it.isNotBlank() }

    if (serverDir.isPresent) {
        dependsOn(tasks.shadowJar)
        from(tasks.shadowJar.flatMap { it.archiveFile })
        into(serverDir.map { File(it, "plugins") })
        rename { "DEHPlugin.jar" }
        doFirst {
            val pluginsDir = File(serverDir.get(), "plugins")
            pluginsDir.list()
                ?.filter { it.startsWith("DEHPlugin") && it.endsWith(".jar") }
                ?.forEach { stale ->
                    logger.lifecycle("Removing stale plugin jar: $stale")
                    File(pluginsDir, stale).delete()
                }
        }
    } else {
        logger.warn("deployDevServer: no dev server configured, skipping. " +
            "Set devServer in gradle.properties or the DEH_SERVER env var.")
    }
}
