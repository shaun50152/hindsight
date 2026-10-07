plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
}
