package dev.andrewohara.cats

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.security.SecureRandom
import java.time.Clock
import kotlin.random.asKotlinRandom
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.lens.secret
import org.http4k.lens.string
import kotlin.random.Random

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().optional("DB_USER")
val dbPass = EnvironmentKey.secret().optional("DB_PASS")

fun createApp(
    env: Environment,
    clock: Clock,
    random: Random
): CatService {
    val dbConfig = HikariConfig().apply {
        jdbcUrl = env[dbUrl]
        username = env[dbUser]
        password = env[dbPass]?.use { it }
    }

    val database = HikariDataSource(dbConfig)
        .asJdbcDriver()
        .also { Database.Schema.create(it) }
        .let { Database(it) }

    return CatService(
        cats = CatsRepo(database.catsQueries),
        clock = clock,
        random = random
    )
}

fun main() {
    val service = createApp(
        env = Environment.ENV,
        random = SecureRandom().asKotlinRandom(),
        clock = Clock.systemUTC()
    )

    service.toApi()
        .asServer(JettyLoom(8000))
        .start()
}