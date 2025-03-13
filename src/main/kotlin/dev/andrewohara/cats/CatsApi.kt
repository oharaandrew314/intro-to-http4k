package dev.andrewohara.cats

import org.http4k.core.*
import org.http4k.format.Moshi
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.uuid
import org.http4k.routing.bind
import org.http4k.routing.routes

val idLens = Path.uuid().of("cat_id")
val catsLens = Body.auto<Array<Cat>>().toLens()
val catLens = Body.auto<Cat>().toLens()
val catDataLens = Body.auto<CatData>().toLens()

fun CatService.toApi(): HttpHandler {
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
        "v1/cats" bind Method.POST to { request ->
            val cat = createCat(catDataLens(request))

            Response(Status.OK).with(catLens of cat)
        },
        "v1/cats/$idLens" bind Method.DELETE to { request ->
            deleteCat(idLens(request))
                ?.let { Response(Status.OK).with(catLens of it) }
                ?: Response(Status.NOT_FOUND)
        }
    )
}