plugins {
    kotlin("jvm") version "2.1.10"
    id("app.cash.sqldelight") version "2.0.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.http4k:http4k-bom:6.1.0.1"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")
    implementation("app.cash.sqldelight:jdbc-driver:2.0.2")
    implementation("com.zaxxer:HikariCP:6.2.1")
    runtimeOnly("mysql:mysql-connector-java:8.0.33")
    implementation("org.http4k:http4k-config")
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.2")

    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("org.http4k:http4k-testing-approval")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("dev.andrewohara.cats")
            dialect("app.cash.sqldelight:mysql-dialect:2.0.2")
        }
    }
}