plugins {
    `java-library`
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(libs.jqwik)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
