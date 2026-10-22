package net.zodac.dicefive.purchases

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * What the store last said this device's player owns, in a file of its own
 * ([net.zodac.dicefive.data.PreferencesFile.PURCHASES]) so no reset button touches it. Never in the cloud save: the store
 * account owns the purchase, and may not be the online game service's account.
 */
class PurchasesRepository(private val dataStore: DataStore<Preferences>) {

    /** Whether every style is unlocked by [ProductKey.UNLOCK_ALL_STYLES]: false until the store first says so. */
    val ownsAllStyles: Flow<Boolean> = dataStore.data.map { it[OWNS_ALL_STYLES_KEY] ?: false }

    suspend fun setOwnsAllStyles(owns: Boolean) {
        dataStore.edit { it[OWNS_ALL_STYLES_KEY] = owns }
    }

    /**
     * Keeps [ownsAllStyles] to what [store] says, for as long as it's collected: set once it's [StoreState.Owned], cleared
     * once it answers that it isn't (a refund, a pending payment, another store account). A store that hasn't answered,
     * or isn't there, changes nothing.
     */
    suspend fun follow(store: Store) {
        store.state.mapNotNull { it.ownsAllStyles }.distinctUntilChanged().collect { setOwnsAllStyles(it) }
    }

    private companion object {
        val OWNS_ALL_STYLES_KEY = booleanPreferencesKey("owns_all_styles")
    }
}
