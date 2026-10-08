plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
}

dependencies {
    api(project(":common"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.cel)
    implementation(libs.protobuf.java)

    testImplementation(libs.jqwik)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
