package dev.andrewohara.cats

import org.http4k.core.*
import org.http4k.filter.ServerFilters
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.RequestKey
import org.http4k.lens.uuid
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val idLens = Path.uuid().of("cat_id")
val catsLens = Body.auto<Array<Cat>>().toLens()
val catLens = Body.auto<Cat>().toLens()
val catDataLens = Body.auto<CatData>().toLens()

fun CatService.toApi(): RoutingHttpHandler {
    val userIdLens = RequestKey.required<String>("name")

    return routes(
        "/v1/cats" bind Method.GET to {
            val result = listCats().toTypedArray()
            Response(Status.OK)
                .with(catsLens of result)
        },
        "/v1/cats/$idLens" bind Method.GET to { request ->
            val id = idLens(request)
            val cat = getCat(id)

            if (cat == null) {
                Response(Status.NOT_FOUND)
            } else {
                Response(Status.OK)
                    .with(catLens of cat)
            }
        },
        ServerFilters.BearerAuth(userIdLens, authorizer::verify).then(routes(
            "v1/cats" bind Method.POST to { request ->
                val userId = userIdLens(request)
                val cat = createCat(userId, catDataLens(request))

                Response(Status.OK).with(catLens of cat)
            },
            "v1/cats/$idLens" bind Method.DELETE to fn@{ request ->
                val cat = getCat(idLens(request)) ?: return@fn Response(Status.NOT_FOUND)
                if (cat.userId != userIdLens(request)) return@fn Response(Status.FORBIDDEN)
                deleteCat(idLens(request))

                Response(Status.OK).with(catLens of cat)
            }
        ))
    )
}