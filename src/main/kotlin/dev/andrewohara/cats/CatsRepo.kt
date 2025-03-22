package dev.andrewohara.cats

import java.time.ZoneOffset
import java.util.UUID

class CatsRepo(private val queries: CatsQueries) {

    fun listCats(): List<Cat> {
        return queries.listCats()
            .executeAsList()
            .map { it.toModel() }
    }

    fun getCat(id: UUID): Cat? {
        return queries.getCat(id.toString())
            .executeAsOneOrNull()
            ?.toModel()
    }

    fun saveCat(cat: Cat) {
        queries.createCat(
            id = cat.id.toString(),
            user_id = cat.userId,
            birth_date = cat.dateOfBirth,
            created_at = cat.createdAt.atOffset(ZoneOffset.UTC),
            name = cat.name,
            breed = cat.breed,
            colour = cat.colour
        )
    }

    fun deleteCat(id: UUID) {
        queries.deleteCat(id.toString())
    }
}

private fun Cats.toModel() = Cat(
    id = UUID.fromString(id),
    userId = user_id,
    name = name,
    createdAt = created_at.toInstant(),
    dateOfBirth = birth_date,
    breed = breed,
    colour = colour
)