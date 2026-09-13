plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.commons.compress)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
