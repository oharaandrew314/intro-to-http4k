package dev.andrewohara.cats

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
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
import org.http4k.lens.uri
import org.http4k.routing.routes
import java.net.URI
import kotlin.random.Random

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().optional("DB_USER")
val dbPass = EnvironmentKey.secret().optional("DB_PASS")
val issuer = EnvironmentKey.string().required("ISSUER")
val clientId = EnvironmentKey.string().required("CLIENT_ID")
val jwkUri = EnvironmentKey.uri().required("JWK_URI")
val redirectUri = EnvironmentKey.uri().required("REDIRECT_URI")

fun createApp(
    env: Environment,
    clock: Clock,
    random: Random,
    keySelector: JWSKeySelector<SecurityContext>? = null
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

    val authorizer = Authorizer(
        issuer = env[issuer],
        audience = listOf(env[clientId]),
        keySelector = keySelector ?: JWSVerificationKeySelector(
            JWSAlgorithm.RS256,
            JWKSourceBuilder
                .create<SecurityContext>(URI.create(env[jwkUri].toString()).toURL())
                .build()
        )
    )

    return CatService(
        cats = CatsRepo(database.catsQueries),
        clock = clock,
        random = random,
        authorizer = authorizer
    )
}

fun main() {
    val env = Environment.ENV

    val service = createApp(
        env = env,
        random = SecureRandom().asKotlinRandom(),
        clock = Clock.systemUTC()
    )

    val webApp = webApp(
        clientId = env[clientId],
        redirectUri = env[redirectUri]
    )

    routes(service.toApi(), webApp)
        .asServer(JettyLoom(8000))
        .start()
}