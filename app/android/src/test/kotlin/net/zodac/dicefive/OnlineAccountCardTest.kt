package net.zodac.dicefive

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    var signOutCalls = 0
    override suspend fun signOut() {
        signOutCalls++
    }
}

/** The Settings card for the online service: hidden with no service, otherwise named by the service and fully spoken. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class OnlineAccountCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(services: GameServices) {
        compose.setContent {
            DiceFiveTheme { OnlineAccountCard(services) }
        }
    }

    @Test
    fun `no service draws nothing`() {
        show(NoGameServices)
        compose.onNodeWithText("Not signed in").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun `signed out offers sign in and nothing else`() {
        val services = FakeGameServices(GameServicesState.SignedOut)
        show(services)
        // The header is one node for TalkBack (its pieces cleared), so its words are read from the unmerged tree.
        compose.onNodeWithText("Test Games", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Not signed in", useUnmergedTree = true).assertExists()
        assertEquals(listOf("Test Games, Not signed in"), compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
        compose.onNodeWithText("Sign in").performClick()
        compose.waitForIdle()
        assertEquals(1, services.signInCalls)
    }

    @Test
    fun `signed in is one heading that speaks the full name`() {
        val longName = "A very long gamer name indeed"
        show(FakeGameServices(GameServicesState.SignedIn(OnlineAccount(longName, null, "key"))))
        compose.onNodeWithText("Signed in as $longName", useUnmergedTree = true).assertExists()
        val header = compose.onNode(isHeading())
        assertEquals(listOf("Test Games, signed in as $longName"), header.fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `signed in offers sign out and nothing else`() {
        val services = FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k")))
        show(services)
        compose.onNodeWithText("Sign in").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.onNode(hasText("Sign out") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, services.signOutCalls)
    }

    @Test
    fun `signing in offers neither button`() {
        show(FakeGameServices(GameServicesState.SigningIn))
        compose.onNodeWithText("Signing in...", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sign in").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }
}
