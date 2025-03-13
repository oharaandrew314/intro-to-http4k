package dev.andrewohara.cats

import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
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

private const val ULTIMATE_NUMBER = 42

@ExtendWith(JsonApprovalTest::class)
class CatsTest {

    private val service = CatService(
        Clock.fixed(Instant.parse("2025-03-25T12:00:00Z"), ZoneOffset.UTC),
        Random(ULTIMATE_NUMBER)
    )

    private val api = service.toApi()

    @Test
    fun `list cats`() {
        val cat1 = service.createCat(CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        val cat2 = service.createCat(CatData(
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
        val cat1 = service.createCat(CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        service.createCat(CatData(
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
    fun `create cat`(approver: Approver) {
        val response = Request(Method.POST, "/v1/cats").with(catDataLens of CatData(
            name = "Toggles",
            dateOfBirth = LocalDate.of(2004, 6, 1),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        )).let(api)

        approver.assertApproved(response, Status.OK)

        service.listCats().shouldHaveSize(1)
    }

    @Test
    fun `delete cat - not found`() {
        Request(Method.DELETE, "/v1/cats/c737486a-2988-472a-b580-7bb3e7adfd17")
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete cat - success`(approver: Approver) {
        val cat1 = service.createCat(CatData(
            name = "Kratos",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Lynx Point Tabby"
        ))
        val cat2 = service.createCat(CatData(
            name = "Athena",
            dateOfBirth = LocalDate.of(2022, 9, 22),
            breed = "American Shorthair",
            colour = "Brown Tabby"
        ))

        Request(Method.DELETE, "/v1/cats/${cat2.id}")
            .let(api)
            .also { approver.assertApproved(it, Status.OK) }

        service.listCats().shouldContainExactly(cat1)
    }
}