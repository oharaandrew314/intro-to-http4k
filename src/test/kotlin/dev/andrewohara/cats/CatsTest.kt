package dev.andrewohara.cats

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.http4k.base64Encode
import org.http4k.config.Environment
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Moshi
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.lens.bearerAuth
import org.http4k.testing.Approver
import org.http4k.testing.JsonApprovalTest
import org.http4k.testing.assertApproved
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals


private const val ULTIMATE_NUMBER = 42
private const val ISSUER = "cats_idp"
private const val AUDIENCE = "cats_app"

@ExtendWith(JsonApprovalTest::class)
class CatsTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    private fun createToken(userId: String): String {
        val algorithm = Algorithm.RSA256(null, keyPair.private as RSAPrivateKey)
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .sign(algorithm)
    }

    private val service = createApp(
        env = Environment.defaults(
            dbUrl of "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            publicKey of keyPair.public.encoded.base64Encode(),
            issuer of ISSUER,
            audience of AUDIENCE
        ),
        clock = Clock.fixed(Instant.parse("2025-03-25T12:00:00Z"), ZoneOffset.UTC),
        random = Random(ULTIMATE_NUMBER)
    )

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
        Request(Method.POST, "/v1/cats").with(catDataLens of CatData(
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
        val response = Request(Method.POST, "/v1/cats").with(catDataLens of CatData(
            name = "Toggles",
            dateOfBirth = LocalDate.of(2004, 6, 1),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        ))
            .bearerAuth(createToken("user1"))
            .let(api)

        approver.assertApproved(response, Status.OK)

        service.listCats().shouldHaveSize(1)
    }

    @Test
    fun `delete cat - unauthorized due to no token`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete cat - unauthorized due to bad token`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .bearerAuth("foo")
            .let(api)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete cat - not found`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .bearerAuth(createToken("user1"))
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete cat - forbidden`() {
        val cat1 = service.createCat("user2", CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))

        Request(Method.DELETE, "/v1/cats/${cat1.id}")
            .bearerAuth(createToken("user1"))
            .let(api)
            .shouldHaveStatus(Status.FORBIDDEN)
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

    @Test
    fun `generate openapi spec`(approver: Approver) {
        Request(Method.GET, "/openapi.json")
            .let(api)
            .also { approver.assertApproved(it, Status.OK) }
    }
}