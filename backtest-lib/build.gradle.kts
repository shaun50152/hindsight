plugins {
    `java-library`
}

dependencies {
    api(project(":common"))
    api(project(":policy-engine"))
    implementation(libs.jackson.databind)
    implementation("org.springframework:spring-jdbc:6.2.7")
    implementation("org.apache.kafka:kafka-clients:3.8.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
