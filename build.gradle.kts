plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

subprojects {
    group = "dev.hindsight"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
