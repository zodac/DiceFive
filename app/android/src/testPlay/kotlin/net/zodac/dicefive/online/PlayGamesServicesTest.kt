package net.zodac.dicefive.online

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotContents
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.online.PlayGamesServices.Companion.conflictOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Which builds and devices get Play Games, and what the device keeps of the account. */
@RunWith(AndroidJUnit4::class)
class PlayGamesServicesTest {

    @Test
    fun `a debug build never uses Play Games - however it was installed`() {
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = true, installer = null))
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = true, installer = "com.example.files"))
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = true, installer = "com.android.vending"))
    }

    @Test
    fun `a release build uses Play Games only when installed from Google Play`() {
        assertTrue(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = false, installer = "com.android.vending"))
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = false, installer = null))
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = true, isDebugBuild = false, installer = "com.google.android.packageinstaller"))
    }

    @Test
    fun `no build uses Play Games without Google Play services`() {
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = false, isDebugBuild = true, installer = "com.android.vending"))
        assertFalse(PlayGamesServices.usesPlayGames(playServicesAvailable = false, isDebugBuild = false, installer = "com.android.vending"))
    }

    @Test
    fun `a device without Google Play services gets no service and no Settings card`() {
        // Robolectric has no Play services, as a de-Googled phone hasn't.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val services = PlayGamesServices.attach(activity)
        assertSame(NoGameServices, services)
        assertEquals(GameServicesState.Unavailable, services.state.value)
    }

    @Test
    fun `the SDK can't start itself at launch - only once the player signs in`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
        val providers = context.packageManager.getPackageInfo(context.packageName, flags).providers.orEmpty()
        val init = providers.single { it.name == "com.google.android.gms.games.provider.PlayGamesInitProvider" }
        assertFalse("PlayGamesInitProvider is enabled", init.enabled)
    }

    @Test
    fun `an opened save with no conflict is read as none - Play's own getConflict would throw`() {
        val snapshot = stub<Snapshot>()
        val opened = SnapshotsClient.DataOrConflict<Snapshot>(snapshot, null)
        assertTrue(runCatching { opened.conflict }.exceptionOrNull() is IllegalStateException)
        assertNull(opened.conflictOrNull())
        val conflict = SnapshotsClient.SnapshotConflict(snapshot, "conflict-1", stub<Snapshot>(), stub<SnapshotContents>())
        assertSame(conflict, SnapshotsClient.DataOrConflict<Snapshot>(null, conflict).conflictOrNull())
    }

    @Test
    fun `the account key is a stable hash that never holds the player id`() {
        val key = PlayGamesServices.accountKey("a_player_id_1234")
        assertEquals(key, PlayGamesServices.accountKey("a_player_id_1234"))
        assertNotEquals(key, PlayGamesServices.accountKey("a_player_id_1235"))
        assertFalse(key.contains("a_player_id_1234"))
        assertEquals(64, key.length)
    }

    @Test
    fun `a save read or left unchanged is discarded - and the discard waited for`() {
        val (client, calls) = recordingSnapshotsClient()
        var awaited = 0
        val result = runBlocking { client.closingSnapshot(stub<Snapshot>(), awaitDiscard = { awaited++ }) { "read" } }
        assertEquals("read", result)
        assertEquals(listOf("discardAndClose"), calls)
        assertEquals(1, awaited)
    }

    @Test
    fun `a committed save is closed by its commit alone`() {
        val (client, calls) = recordingSnapshotsClient()
        var awaited = 0
        runBlocking { client.closingSnapshot(stub<Snapshot>(), awaitDiscard = { awaited++ }) { commit -> commit() } }
        assertEquals(listOf("commitAndClose"), calls)
        assertEquals(0, awaited)
    }

    @Test
    fun `a save whose merge throws is still discarded - and the error goes on`() {
        val (client, calls) = recordingSnapshotsClient()
        var awaited = 0
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { client.closingSnapshot<Unit>(stub<Snapshot>(), awaitDiscard = { awaited++ }) { error("unreadable save") } }
        }
        assertEquals("unreadable save", error.message)
        assertEquals(listOf("discardAndClose"), calls)
        assertEquals(0, awaited)
    }

    /** A [SnapshotsClient] that records the name of each call made on it, every one completing at once. */
    private fun recordingSnapshotsClient(): Pair<SnapshotsClient, List<String>> {
        val calls = mutableListOf<String>()
        val client = Proxy.newProxyInstance(SnapshotsClient::class.java.classLoader, arrayOf(SnapshotsClient::class.java)) { _, method, _ ->
            calls += method.name
            Tasks.forResult(null)
        } as SnapshotsClient
        return client to calls
    }

    /** A [T] whose every method returns null: Play's types are interfaces, and these tests never call them. */
    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T
}
