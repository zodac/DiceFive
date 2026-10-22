package net.zodac.dicefive.purchases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences

/** What the device keeps of the store's answers: the entitlement every style lock reads. */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchasesRepositoryTest {

    @Test
    fun nothingIsOwnedUntilTheStoreSaysSo() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(StoreState.Loading)
        follow(repository, store)
        assertFalse(repository.ownsAllStyles.first())
        store.state.value = StoreState.ForSale(price = "£2.99")
        runCurrent()
        assertFalse(repository.ownsAllStyles.first())
    }

    @Test
    fun anOwnedPurchaseUnlocksEveryStyle() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(StoreState.ForSale(price = "£2.99"))
        follow(repository, store)
        store.state.value = StoreState.Owned
        runCurrent()
        assertTrue(repository.ownsAllStyles.first())
    }

    @Test
    fun aRefundClearsIt() = runTest {
        val repository = owner()
        val store = FakeStore(StoreState.Owned)
        follow(repository, store)
        store.state.value = StoreState.ForSale(price = null)
        runCurrent()
        assertFalse(repository.ownsAllStyles.first())
    }

    @Test
    fun aStoreThatHasNotAnsweredOrIsNotThereKeepsIt() = runTest {
        val repository = owner()
        val store = FakeStore(StoreState.Loading)
        follow(repository, store)
        store.state.value = StoreState.Unavailable
        runCurrent()
        assertTrue(repository.ownsAllStyles.first())
    }

    @Test
    fun aPendingPaymentNeverGrantsIt() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(StoreState.ForSale(price = "£2.99"))
        follow(repository, store)
        store.state.value = StoreState.Pending
        runCurrent()
        assertFalse(repository.ownsAllStyles.first())
        store.state.value = StoreState.Owned
        runCurrent()
        assertTrue(repository.ownsAllStyles.first())
    }

    @Test
    fun onlyAnAnswerFromTheStoreSaysWhetherItIsOwned() {
        assertEquals(true, StoreState.Owned.ownsAllStyles)
        assertEquals(false, StoreState.ForSale(price = "£2.99").ownsAllStyles)
        assertEquals(false, StoreState.ForSale(price = null).ownsAllStyles)
        assertEquals(false, StoreState.Pending.ownsAllStyles)
        assertNull(StoreState.Loading.ownsAllStyles)
        assertNull(StoreState.Unavailable.ownsAllStyles)
    }

    @Test
    fun noStoreSellsNothing() {
        assertEquals(StoreState.Unavailable, NoStore.state.value)
    }

    private suspend fun owner() = PurchasesRepository(InMemoryPreferences()).apply { setOwnsAllStyles(true) }

    /** [repository] follows [store] for the rest of the test, having taken its current state. */
    private fun TestScope.follow(repository: PurchasesRepository, store: Store) {
        backgroundScope.launch { repository.follow(store) }
        runCurrent()
    }

    private class FakeStore(initial: StoreState) : Store {
        override val state = MutableStateFlow(initial)

        override fun buy() = Unit
    }
}
