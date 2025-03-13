package dev.andrewohara.cats

import org.http4k.server.JettyLoom
import org.http4k.server.asServer
import java.security.SecureRandom
import java.time.Clock
import kotlin.random.asKotlinRandom

fun main() {
    val api = CatService(
        clock = Clock.systemUTC(),
        random = SecureRandom().asKotlinRandom()
    ).toApi()

    api
        .asServer(JettyLoom(8000))
        .start()
}