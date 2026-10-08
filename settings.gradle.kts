rootProject.name = "hindsight"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}

include(
    "common",
    "backtest-lib",
    "policy-engine",
    "policy-service",
    "decision-service",
    "audit-service",
    "simulation-service",
    "backtest-worker",
    "synth-data"
)
