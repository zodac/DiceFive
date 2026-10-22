package net.zodac.dicefive.purchases

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No store: previews, tests, the github app, and a device without one. Nothing is for sale. */
object NoStore : Store {

    override val state: StateFlow<StoreState> = MutableStateFlow(StoreState.Unavailable)

    override fun buy() = Unit
}
