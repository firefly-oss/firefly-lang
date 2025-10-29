plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.firefly"
version = "1.0-Alpha"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")

    intellijPlatform {
        intellijIdeaCommunity("2023.2.5")

        // Plugin Dependencies
        bundledPlugin("com.intellij.java")

        // Required for plugin development
        instrumentationTools()
        pluginVerifier()
    }
}

// Configure IntelliJ Platform Plugin
intellijPlatform {
    pluginConfiguration {
        name = "Firefly"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "232"
            untilBuild = "241.*"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}

