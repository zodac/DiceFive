package net.zodac.dicefive.purchases

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared game needs from a platform's app store - Google Play Billing on Android - for its one purchase,
 * [ProductKey.UNLOCK_ALL_STYLES], and nothing that names it. Each platform implements it once; [NoStore] is the version
 * for previews, tests and builds with no store. See `.claude/PURCHASES.md`.
 *
 * The store is never the source of truth while playing: [PurchasesRepository] keeps what it last said, and the game
 * reads only that. No call throws; a failure leaves [state] as it was.
 */
interface Store {

    val state: StateFlow<StoreState>

    /** Opens the store's own purchase sheet, while [state] is [StoreState.ForSale] with a price; otherwise does nothing. */
    fun buy()
}

/** What the store says about [ProductKey.UNLOCK_ALL_STYLES] on the player's store account. */
sealed interface StoreState {

    /** There is no store on this device (another distribution, no Play Store, a preview). Nothing is offered. */
    data object Unavailable : StoreState

    /** The store hasn't answered yet. */
    data object Loading : StoreState

    /** Not owned. [price] is the store's own localised price, or null while it couldn't be fetched. */
    data class ForSale(val price: String?) : StoreState

    /** Bought, but not paid yet (a cash or slow payment method): nothing is granted until it is [Owned]. */
    data object Pending : StoreState

    data object Owned : StoreState
}

/**
 * Whether this state says the account owns every style: true for [StoreState.Owned], false where the store answered
 * that it doesn't, and null where it hasn't answered, which changes nothing.
 */
val StoreState.ownsAllStyles: Boolean?
    get() = when (this) {
        StoreState.Owned -> true
        is StoreState.ForSale, StoreState.Pending -> false
        StoreState.Unavailable, StoreState.Loading -> null
    }

/** The store's product ids. Permanent once created in the store, even if deleted there. */
object ProductKey {

    /** The one-time purchase that unlocks every style and colour. Never consumed, so it's bought once per account. */
    const val UNLOCK_ALL_STYLES = "unlock_all_styles"
}

val LocalStore = staticCompositionLocalOf<Store> { NoStore }
