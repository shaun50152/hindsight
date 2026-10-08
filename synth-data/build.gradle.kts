plugins {
    application
    java
}

application {
    mainClass = "dev.hindsight.synthdata.SynthDataMain"
}

dependencies {
    implementation(project(":common"))
    implementation(libs.jackson.databind)

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
