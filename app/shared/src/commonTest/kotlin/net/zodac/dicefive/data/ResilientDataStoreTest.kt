package net.zodac.dicefive.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.IOException

class ResilientDataStoreTest {

    private val key = intPreferencesKey("count")

    /** A file that reads as [stored], but can't be written - or, with [readable] false, read either. */
    private class FailingDataStore(private val stored: Preferences, private val readable: Boolean = true) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { if (readable) emit(stored) else throw IOException("I/O error") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("No space left on device")
    }

    @Test
    fun `a write that can't be made is skipped - the file reads as it was - rather than thrown`() = runTest {
        val store = ResilientDataStore(FailingDataStore(preferencesOf(key to 1)))
        store.edit { it[key] = 2 }
        assertEquals(1, store.data.first()[key])
    }

    @Test
    fun `a read that can't be made is empty preferences`() = runTest {
        val store = ResilientDataStore(FailingDataStore(preferencesOf(key to 1), readable = false))
        assertEquals(emptyPreferences(), store.data.first())
        store.edit { it[key] = 2 }
    }

    /** A file that can't be read the first [failures] times it's asked for, and then reads as [stored]. Counts its [reads]. */
    private class FlakyDataStore(private val stored: Preferences, private var failures: Int) : DataStore<Preferences> {
        var reads = 0

        override val data: Flow<Preferences> = flow {
            reads++
            if (failures-- > 0) throw IOException("I/O error") else emit(stored)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(stored)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime
    @Test
    fun `a read that fails is tried again after 1s, 3s and 5s - the collector sees empty preferences, then the file`() = runTest {
        val file = FlakyDataStore(preferencesOf(key to 1), failures = 3)
        val store = ResilientDataStore(file)
        assertEquals(listOf(emptyPreferences(), preferencesOf(key to 1)), store.data.take(2).toList())
        assertEquals(4, file.reads)
        assertEquals(9_000L, testScheduler.currentTime)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime
    @Test
    fun `a read that still fails after three retries is tried no more - the collector is left with empty preferences`() = runTest {
        val file = FlakyDataStore(preferencesOf(key to 1), failures = 10)
        val store = ResilientDataStore(file)
        assertEquals(listOf(emptyPreferences()), store.data.toList())
        assertEquals(4, file.reads)
        assertEquals(9_000L, testScheduler.currentTime)
    }

    @Test
    fun `a failure reading that isn't the file's is still thrown`() = runTest {
        val store = ResilientDataStore(
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { error("a bug") }

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = error("unused")
            },
        )
        assertFailsWith<IllegalStateException> { store.data.first() }
    }

    @Test
    fun `a working file reads and writes as ever - and a failure that isn't the file's is still thrown`() = runTest {
        val store = ResilientDataStore(InMemoryPreferences())
        store.edit { it[key] = 2 }
        assertEquals(2, store.data.first()[key])
        assertFailsWith<IllegalStateException> { store.edit { error("a bug") } }
    }
}
