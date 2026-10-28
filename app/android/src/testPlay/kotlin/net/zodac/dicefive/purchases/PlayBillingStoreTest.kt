package net.zodac.dicefive.purchases

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.Purchase
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import net.zodac.dicefive.purchases.PlayBillingStore.Companion.storeState
import net.zodac.dicefive.purchases.PlayBillingStore.Companion.unlockAllStyles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Which builds and devices get Play Billing, and what Play's purchases mean for the app. */
@RunWith(AndroidJUnit4::class)
class PlayBillingStoreTest {

    @Test
    fun `a release build uses Play Billing only when installed from Google Play`() {
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = "com.android.vending"))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = null))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = "com.google.android.packageinstaller"))
    }

    @Test
    fun `a debug build uses Play Billing however it was installed - for a license tester`() {
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = null))
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = "com.example.files"))
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = "com.android.vending"))
    }

    @Test
    fun `no build uses Play Billing without the Play Store`() {
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = false, isDebugBuild = true, installer = "com.android.vending"))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = false, isDebugBuild = false, installer = "com.android.vending"))
    }

    @Test
    fun `a device without the Play Store gets no store - so nothing is for sale`() {
        // Robolectric has no Play Store, as the emulator and a de-Googled phone haven't.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val store = PlayBillingStore.attach(activity)
        assertSame(NoStore, store)
        assertEquals(StoreState.Unavailable, store.state.value)
    }

    @Test
    fun `the app asks for the billing permission - and still not the network, which only the library's telemetry wants`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains("com.android.vending.BILLING"))
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun `only a paid purchase owns every style - a pending one is waiting - and none is for sale at Play's price`() {
        assertEquals(StoreState.Owned, storeState(purchase(PURCHASED), price = "£2.99"))
        assertEquals(StoreState.Pending, storeState(purchase(PENDING), price = "£2.99"))
        assertEquals(StoreState.ForSale("£2.99"), storeState(null, price = "£2.99"))
        assertEquals(StoreState.ForSale(null), storeState(null, price = null))
    }

    @Test
    fun `the purchase that counts is this product's - a paid one before a pending one`() {
        val other = purchase(PURCHASED, product = "some_other_product")
        assertNull(listOf(other).unlockAllStyles())
        assertNull(emptyList<Purchase>().unlockAllStyles())
        val pending = purchase(PENDING, token = "pending")
        val paid = purchase(PURCHASED, token = "paid")
        assertEquals("paid", listOf(other, pending, paid).unlockAllStyles()?.purchaseToken)
        assertEquals("pending", listOf(other, pending).unlockAllStyles()?.purchaseToken)
    }

    @Test
    fun `with a licence key only a purchase it signed counts - one signed by another key, or changed since, doesn't`() {
        val play = keyPair()
        val signed = purchase(PURCHASED, signedBy = play)
        assertSame(signed, listOf(signed).unlockAllStyles(licenceKey = play.licenceKey))
        assertNull(listOf(purchase(PURCHASED, signedBy = keyPair())).unlockAllStyles(licenceKey = play.licenceKey))
        assertNull(listOf(Purchase(signed.originalJson.replace("\"token\"", "\"forged\""), signed.signature)).unlockAllStyles(licenceKey = play.licenceKey))
        assertNull(listOf(purchase(PURCHASED)).unlockAllStyles(licenceKey = play.licenceKey))
    }

    @Test
    fun `a licence key that can't be read counts no purchase - and with none, every purchase counts`() {
        val signed = purchase(PURCHASED, signedBy = keyPair())
        assertNull(listOf(signed).unlockAllStyles(licenceKey = "not a key"))
        assertSame(signed, listOf(signed).unlockAllStyles(licenceKey = ""))
    }

    private val KeyPair.licenceKey: String get() = Base64.getEncoder().encodeToString(public.encoded)

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** A purchase as Play sends it: its JSON's purchaseState is 0 once paid and 4 while pending - signed by [signedBy], if given. */
    private fun purchase(state: Int, product: String = ProductKey.UNLOCK_ALL_STYLES, token: String = "token", signedBy: KeyPair? = null): Purchase {
        val json = JSONObject()
            .put("orderId", "GPA.0000-0000-0000-00000")
            .put("packageName", "net.zodac.dicefive")
            .put("productId", product)
            .put("purchaseTime", 1_790_000_000_000L)
            .put("purchaseState", state)
            .put("purchaseToken", token)
            .put("acknowledged", false)
            .toString()
        val signature = signedBy?.let { pair ->
            Signature.getInstance("SHA1withRSA").run {
                initSign(pair.private)
                update(json.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sign())
            }
        }
        return Purchase(json, signature ?: "signature")
    }

    private companion object {
        const val PURCHASED = 0
        const val PENDING = 4
    }
}
