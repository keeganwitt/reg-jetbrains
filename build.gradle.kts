import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "com.keeganwitt"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin { jvmToolchain(25) }

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.keeganwitt.reg"
        name = "Windows Registry"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "263"
            untilBuild = provider { null }
        }
        vendor { name = "Keegan Witt" }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion").get())
            create(IntelliJPlatformType.PyCharm, providers.gradleProperty("platformVersion").get())
            create(IntelliJPlatformType.WebStorm, providers.gradleProperty("platformVersion").get())
        }
    }
}

tasks.test { maxHeapSize = "2g" }

tasks.processResources {
    from("LICENSE") { into("META-INF") }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>("verifyPlugin") {
    offline = true
}
