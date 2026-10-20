package net.zodac.dicefive.ui.common

/** Keeps the one value [get] built for the last key it was asked for, and builds a new one when the key changes. */
internal class LastKeyCache<K, V : Any> {

    private var entry: Pair<K, V>? = null

    fun get(key: K, create: (K) -> V): V =
        entry?.takeIf { it.first == key }?.second ?: create(key).also { entry = key to it }
}
