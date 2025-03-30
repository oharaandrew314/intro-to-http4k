package dev.andrewohara.cats

import org.http4k.contract.contract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.RequestKey
import org.http4k.lens.uuid
import org.http4k.routing.RoutingHttpHandler
import org.http4k.security.BearerAuthSecurity
import java.time.Instant
import java.time.LocalDate
import java.util.*

val idLens = Path.uuid().of("cat_id")
val catsLens = Body.auto<Array<Cat>>().toLens()
val catLens = Body.auto<Cat>().toLens()
val catDataLens = Body.auto<CatData>().toLens()

private val catDataSample = CatData(
    dateOfBirth = LocalDate.of(2022, 9, 4),
    name = "Kratos",
    breed = "American Shorthair",
    colour = "Lynx Point Tabby"
)

private val catSample = Cat(
    id = UUID.fromString("8614ebfa-5ad5-4a9d-8080-21dfaf950046"),
    userId = "user1",
    createdAt = Instant.parse("2025-03-30T12:00:00Z"),
    dateOfBirth = LocalDate.of(2022, 9, 4),
    name = "Kratos",
    breed = "American Shorthair",
    colour = "Lynx Point Tabby"
)

fun CatService.toApi(): RoutingHttpHandler {
    val userIdLens = RequestKey.required<String>("userId")
    val jwtSecurity = BearerAuthSecurity(userIdLens, ::verify, "googleAuth")

    return contract {
        descriptionPath = "openapi.json"
        renderer = OpenApi3(
            apiInfo = ApiInfo("Cats API", "v1")
        )

        routes += "/v1/cats" meta {
            operationId = "v1ListCats"
            summary = "List Cats"

            returning(Status.OK, catsLens to arrayOf(catSample))
        } bindContract Method.GET to { _: Request ->
            val result = listCats().toTypedArray()
            Response(Status.OK)
                .with(catsLens of result)
        }

        routes += "/v1/cats" / idLens meta {
            operationId = "v1GetCat"
            summary = "Get Cat"

            returning(Status.OK, catLens to catSample)
            returning(Status.NOT_FOUND to "cat not found")
        } bindContract Method.GET to { id ->
            { _: Request ->
                val cat = getCat(id)

                if (cat == null) {
                    Response(Status.NOT_FOUND)
                } else {
                    Response(Status.OK)
                        .with(catLens of cat)
                }
            }
        }

        routes += "/v1/cats" meta {
            operationId = "v1CreateCat"
            summary = "Create Cat"
            security = jwtSecurity

            receiving(catDataLens to catDataSample)

            returning(Status.OK, catLens to catSample)
            returning(Status.UNAUTHORIZED to "bearer token required",)
        } bindContract Method.POST to { request: Request ->
            val userId = userIdLens(request)
            val cat = createCat(userId, catDataLens(request))

            Response(Status.OK).with(catLens of cat)
        }

        routes += "/v1/cats" / idLens meta {
            operationId = "v1DeleteCat"
            summary = "Delete Cat"
            security = jwtSecurity

            returning(Status.OK, catLens to catSample)
            returning(
                Status.UNAUTHORIZED to "bearer token required",
                Status.FORBIDDEN to "don't have permission to delete this cat"
            )
        } bindContract Method.DELETE to { id ->
            fn@{ request ->
                val userId = userIdLens(request)
                val cat = getCat(id) ?: return@fn Response(Status.NOT_FOUND)
                if (userId != cat.userId) return@fn Response(Status.FORBIDDEN)

                deleteCat(id)
                    ?.let { Response(Status.OK).with(catLens of it) }
                    ?: Response(Status.NOT_FOUND)
            }
        }
    }
}