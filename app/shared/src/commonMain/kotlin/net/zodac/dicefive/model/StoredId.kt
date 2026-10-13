package net.zodac.dicefive.model

/**
 * A value saved by its [id]: the string written to the settings or a saved game. Permanent once released, like
 * [GameMode.id] - rename the constant or what players see, never the id.
 */
interface StoredId {
    val id: String
}

/** The [E] saved as [id], or null for one nothing recognises (a value from a newer or damaged save). */
inline fun <reified E> storedIdOf(id: String): E? where E : Enum<E>, E : StoredId = enumValues<E>().firstOrNull { it.id == id }
