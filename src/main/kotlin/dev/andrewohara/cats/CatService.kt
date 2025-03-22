package dev.andrewohara.cats

import java.time.Clock
import java.util.UUID
import kotlin.random.Random

private const val UUID_LENGTH = 40

class CatService(
    private val cats: CatsRepo,
    private val clock: Clock,
    private val random: Random,
    val authorizer: Authorizer
) {

    fun getCat(id: UUID): Cat? {
        return cats.getCat(id)
    }

    fun listCats(): List<Cat> {
        return cats.listCats()
    }

    fun deleteCat(id: UUID): Cat? {
        val cat = cats.getCat(id) ?: return null
        cats.deleteCat(id)
        return cat
    }

    fun createCat(userId: String, data: CatData): Cat {
        val cat = Cat(
            id = UUID.nameUUIDFromBytes(random.nextBytes(UUID_LENGTH)),
            userId = userId,
            createdAt = clock.instant(),
            name = data.name,
            dateOfBirth = data.dateOfBirth,
            breed = data.breed,
            colour = data.colour
        )
        cats.saveCat(cat)
        return cat
    }
}