plugins {
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.http4k:http4k-bom:6.1.0.1"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")

    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("org.http4k:http4k-testing-approval")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}