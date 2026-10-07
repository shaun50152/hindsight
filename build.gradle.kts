// Root build. Per-module build files are added in Phase 1 (via Cursor).
subprojects {
    group = "dev.hindsight"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
