package net.zodac.dicefive

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.OnlineAccount
import net.zodac.dicefive.ui.settings.OnlineAccountCard
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeGameServices(initial: GameServicesState) : GameServices by NoGameServices {
    override val serviceName = "Test Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(initial)
    var signInCalls = 0
    override suspend fun signIn() {
        signInCalls++
    }
}

/** The Settings card for the online service: hidden with no service, otherwise named by the service and fully spoken. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class OnlineAccountCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(services: GameServices, useOnDevice: Boolean = true, cloud: Boolean = true, onUse: (Boolean) -> Unit = {}, onCloud: (Boolean) -> Unit = {}) {
        compose.setContent {
            DiceFiveTheme { OnlineAccountCard(services, useOnDevice, cloud, onUse, onCloud) }
        }
    }

    @Test
    fun `no service draws nothing`() {
        show(NoGameServices)
        compose.onNodeWithText("Not signed in").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun `signed out offers sign in and the device switch only`() {
        val services = FakeGameServices(GameServicesState.SignedOut)
        show(services)
        // The header is one node for TalkBack (its pieces cleared), so its words are read from the unmerged tree.
        compose.onNodeWithText("Test Games", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Not signed in", useUnmergedTree = true).assertExists()
        assertEquals(listOf("Test Games, Not signed in"), compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        compose.onNode(hasText("Use Test Games on this device") and isToggleable()).assertIsOn()
        compose.onNodeWithText("Save progress to the cloud").assertDoesNotExist()
        compose.onNodeWithText("Sign in").performClick()
        compose.waitForIdle()
        assertEquals(1, services.signInCalls)
    }

    @Test
    fun `signed in is one heading that speaks the full name and offers both switches`() {
        val longName = "A very long gamer name indeed"
        show(FakeGameServices(GameServicesState.SignedIn(OnlineAccount(longName, null, "key"))), cloud = false)
        compose.onNodeWithText("Signed in as $longName", useUnmergedTree = true).assertExists()
        val header = compose.onNode(isHeading())
        assertEquals(listOf("Test Games, signed in as $longName"), header.fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        compose.onNode(hasText("Save progress to the cloud") and isToggleable()).assertIsOff()
    }

    @Test
    fun `switches report their change and the cloud switch goes when the device switch is off`() {
        var use by mutableStateOf(true)
        var cloud by mutableStateOf(true)
        compose.setContent {
            DiceFiveTheme {
                OnlineAccountCard(
                    FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k"))),
                    useOnDevice = use, cloudSaveEnabled = cloud, onUseOnDeviceChange = { use = it }, onCloudSaveChange = { cloud = it },
                )
            }
        }
        compose.onNode(hasText("Save progress to the cloud") and isToggleable()).performClick()
        assertEquals(false, cloud)
        compose.onNode(hasText("Use Test Games on this device") and isToggleable()).performClick()
        assertEquals(false, use)
        compose.onNodeWithText("Save progress to the cloud").assertDoesNotExist()
    }
}
