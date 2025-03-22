package dev.andrewohara.cats

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.http4k.format.Moshi
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.testing.Approver
import org.http4k.testing.JsonApprovalTest
import org.http4k.testing.assertApproved
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals
import org.http4k.config.Environment
import org.http4k.core.*
import org.http4k.lens.bearerAuth
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

private const val KEY_SIZE = 2048
private const val ULTIMATE_NUMBER = 42
private const val ISSUER = "test_idp"
private const val CLIENT_ID = "test_client"

@ExtendWith(JsonApprovalTest::class)
class CatsTest {

    private val algorithm = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(KEY_SIZE) }
        .generateKeyPair()
        .let { Algorithm.RSA256(it.public as RSAPublicKey, it.private as RSAPrivateKey) }

    private val service = createApp(
        env = Environment.defaults(
            dbUrl of "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            issuer of ISSUER,
            clientId of CLIENT_ID,
            redirectUri of Uri.of("fake.test")
        ),
        clock = Clock.fixed(Instant.parse("2025-03-25T12:00:00Z"), ZoneOffset.UTC),
        random = Random(ULTIMATE_NUMBER),
        algorithm = algorithm,
    )

    private fun createToken(userId: String, alg: Algorithm = algorithm) = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(CLIENT_ID)
        .withSubject(userId)
        .sign(alg)

    private val api = service.toApi()

    @Test
    fun `list cats`() {
        val cat1 = service.createCat("user1", CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        val cat2 = service.createCat("user1", CatData(
            name = "Athena",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        ))

        val request = Request(Method.GET, "/v1/cats")
        val response = api(request)

        assertEquals(
            Status.OK,
            response.status
        )
        assertEquals(
            listOf(cat1, cat2),
            Moshi.asA<Array<Cat>>(response.bodyString()).toList()
        )
    }

    @Test
    fun `get cat - not found`() {
        Request(Method.GET, "/v1/cats/${UUID.randomUUID()}")
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get cat - found`() {
        val cat1 = service.createCat("user1", CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        service.createCat("user1", CatData(
            name = "Athena",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        ))

        val response = Request(Method.GET, "/v1/cats/${cat1.id}")
            .let(api)

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(catLens, be(cat1))
    }

    @Test
    fun `create cat - unauthorized`() {
        Request(Method.POST, "/v1/cats")
            .with(catDataLens of CatData(
                name = "Toggles",
                dateOfBirth = LocalDate.of(2004, 6, 1),
                breed = "American Shorthair",
                colour = "Brown Tabby"
            ))
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create cat`(approver: Approver) {
        val response = Request(Method.POST, "/v1/cats")
            .header("Authorization", "Bearer ${createToken("user1")}")
            .with(catDataLens of CatData(
                name = "Toggles",
                dateOfBirth = LocalDate.of(2004, 6, 1),
                breed = "American Shorthair",
                colour = "Brown Tabby"
            )).let(api)

        approver.assertApproved(response, Status.OK)

        service.listCats().shouldHaveSize(1)
    }

    @Test
    fun `delete cat - unauthorized`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete cat - not found`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .header("Authorization", "Bearer ${createToken("user1")}")
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete cat - forbidden`() {
        val cat1 = service.createCat("user1", CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))

        Request(Method.DELETE, "/v1/cats/${cat1.id}")
            .header("Authorization", "Bearer ${createToken("user2")}")
            .let(api)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete cat - invalid token`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .bearerAuth("lol")
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete cat - forged token`() {
        val newAlg = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(KEY_SIZE) }
            .generateKeyPair()
            .let { Algorithm.RSA256(it.public as RSAPublicKey, it.private as RSAPrivateKey) }

        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .header("Authorization", "Bearer ${createToken("user1", newAlg)}")
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete cat - success`(approver: Approver) {
        val cat1 = service.createCat("user1", CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        val cat2 = service.createCat("user1", CatData(
            name = "Athena",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        ))

        Request(Method.DELETE, "/v1/cats/${cat2.id}")
            .bearerAuth(createToken("user1"))
            .let(api)
            .also { approver.assertApproved(it, Status.OK) }

        service.listCats().shouldContainExactly(cat1)
    }
}