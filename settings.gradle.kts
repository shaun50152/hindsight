rootProject.name = "hindsight"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}

include(
    "common",
    "policy-service",
    "decision-service",
    "audit-service",
    "simulation-service",
    "backtest-worker"
)
