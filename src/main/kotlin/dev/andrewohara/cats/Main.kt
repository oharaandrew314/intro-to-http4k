package dev.andrewohara.cats

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.base64DecodedArray
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.contract.ui.swaggerUiLite
import org.http4k.lens.base64
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.lens.uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.net.URI
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.asKotlinRandom


val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().optional("DB_USER")
val dbPass = EnvironmentKey.secret().optional("DB_PASS")
val publicKey = EnvironmentKey.base64().optional("PUBLIC_KEY")
val jwksUri = EnvironmentKey.uri().required("JWKS_URI")
val issuer = EnvironmentKey.string().required("ISSUER")
val audience = EnvironmentKey.string().required("AUDIENCE")
val redirectUri = EnvironmentKey.uri().required("REDIRECT_URI")

fun createApp(
    env: Environment,
    clock: Clock,
    random: Random,
): CatService {
    val dbConfig = HikariConfig().apply {
        jdbcUrl = env[dbUrl]
        username = env[dbUser]
        password = env[dbPass]?.use { it }
        maximumPoolSize = 2
    }

    val database = HikariDataSource(dbConfig)
        .asJdbcDriver()
        .also { Database.Schema.create(it) }
        .let { Database(it) }

    val algorithm = env[publicKey]?.let { publicKey ->
        val keySpec = X509EncodedKeySpec(publicKey.base64DecodedArray())
        val javaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        Algorithm.RSA256(javaPublicKey as RSAPublicKey, null)
    } ?: run {
        val provider = JwkProviderBuilder(URI.create(env[jwksUri].toString()).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

        val rsaKeyProvider = object: RSAKeyProvider {
            override fun getPublicKeyById(keyId: String) = provider.get(keyId).publicKey as RSAPublicKey
            override fun getPrivateKey() = null
            override fun getPrivateKeyId() = null
        }

        Algorithm.RSA256(rsaKeyProvider)
    }


    val verifier = JWT.require(algorithm)
        .withIssuer(env[issuer])
        .withAudience(env[audience])
        .build()

    return CatService(
        cats = CatsRepo(database.catsQueries),
        clock = clock,
        random = random,
        jwtVerifier = verifier
    )
}

fun main() {
    val env = Environment.ENV

    val service = createApp(
        env = env,
        random = SecureRandom().asKotlinRandom(),
        clock = Clock.systemUTC()
    )

    val webApp = "ui" bind webApp(
        clientId = env[audience],
        redirectUri = env[redirectUri]
    )

    val swaggerUi = swaggerUiLite {
        pageTitle = "Cats API - Swagger UI"
        url = "openapi.json"
    }

    routes(service.toApi(), webApp, swaggerUi)
        .asServer(JettyLoom(8000))
        .start()
        .also { println("Running on http://localhost:${it.port()}") }
}