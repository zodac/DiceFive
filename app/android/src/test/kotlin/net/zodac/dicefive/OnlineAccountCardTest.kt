package net.zodac.dicefive

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.OnlineAccount
import net.zodac.dicefive.online.SyncProgress
import net.zodac.dicefive.online.SyncStep
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.settings.OnlineAccountCard
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val zone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(zone)
    }

    private fun show(services: GameServices, lastSavedEpochMillis: Long? = null, unreachable: Boolean = false, syncProgress: SyncProgress? = null) {
        compose.setContent {
            DiceFiveTheme { OnlineAccountCard(services, lastSavedEpochMillis = lastSavedEpochMillis, syncProgress = syncProgress, unreachable = unreachable) }
        }
    }

    @Test
    fun `a sync under way shows its step and how far under when it last saved - and is spoken so`() {
        show(
            FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))),
            lastSavedEpochMillis = 1_792_073_100_000L,
            unreachable = true,
            syncProgress = SyncProgress(SyncStep.SCORES, 3, 7),
        )
        compose.onNodeWithText("Merging scores (3/7)", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Last saved Oct", substring = true, useUnmergedTree = true).assertExists()
        // Whether it got through waits for this sync's result.
        compose.onNodeWithText("Couldn't reach", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(
            listOf("Test Games, signed in as Ann, Merging scores, step 3 of 7"),
            compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )
        // The heading says it all, so the step and its bar are hidden from TalkBack, not left to be read twice.
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo((3 - 0.5f) / 7, 0f..1f)), useUnmergedTree = true)
            .assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)))
    }

    /** The sync bar's value as drawn now: the one progress node with a range (the spinner's is indeterminate). */
    private fun barValue(): Float =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.ProgressBarRangeInfo] }
            .single { it.range.endInclusive == 1f }.current

    private fun showSyncing(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                DiceFiveTheme {
                    OnlineAccountCard(FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))), syncProgress = SyncProgress(SyncStep.SCORES, 3, 7))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the sync bar glides to its step`() {
        showSyncing(reduceMotion = false)
        assertTrue(barValue() < (3 - 0.5f) / 7)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals((3 - 0.5f) / 7, barValue(), 0.0001f)
    }

    @Test
    fun `under reduced motion the sync bar snaps to its step`() {
        showSyncing(reduceMotion = true)
        compose.mainClock.advanceTimeByFrame()
        assertEquals((3 - 0.5f) / 7, barValue(), 0.0001f)
    }

    @Test
    fun `every sync step has its own words`() {
        compose.setContent {
            DiceFiveTheme {
                Column {
                    for (step in SyncStep.entries) {
                        OnlineAccountCard(FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))), syncProgress = SyncProgress(step, 1, 1))
                    }
                }
            }
        }
        val spoken = compose.onAllNodes(isHeading()).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].single() }
        assertEquals(SyncStep.entries.size, spoken.toSet().size)
    }

    @Test
    fun `an unreachable service gets a quiet line - spoken last`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        show(FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))), lastSavedEpochMillis = 1_792_073_100_000L, unreachable = true)
        compose.onNodeWithText("Couldn't reach Test Games\u00A0- will retry", useUnmergedTree = true).assertExists()
        assertEquals(
            listOf("Test Games, signed in as Ann, last saved Oct 15, 2026 14:05, couldn't reach Test Games, will retry"),
            compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun `signed out - an unreachable service says nothing`() {
        show(FakeGameServices(GameServicesState.SignedOut()), unreachable = true)
        compose.onNodeWithText("Couldn't reach", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf("Test Games, Not signed in"), compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `a failed sign-in shows the service's reason in red - spoken last - and Sign in stays offered`() {
        var red = Color.Unspecified
        val services = FakeGameServices(GameServicesState.SignedOut(failure = "signIn failed: status 10 (DEVELOPER_ERROR)"))
        compose.setContent {
            DiceFiveTheme {
                red = MaterialTheme.colorScheme.error
                OnlineAccountCard(services)
            }
        }
        val line = "Couldn't sign in: signIn failed: status 10 (DEVELOPER_ERROR)"
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(line, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(red, layouts.single().layoutInput.style.color)
        assertEquals(
            listOf("Test Games, Not signed in, couldn't sign in: signIn failed: status 10 (DEVELOPER_ERROR)"),
            compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )
        compose.onNodeWithText("Sign in").assertIsEnabled()
    }

    @Test
    fun `signed in and saved shows when - and speaks it after the full name`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Oct 15, 2026 14:05 UTC.
        show(FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))), lastSavedEpochMillis = 1_792_073_100_000L)
        compose.onNodeWithText("Ann", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Last saved Oct 15, 2026 14:05", useUnmergedTree = true).assertExists()
        assertEquals(
            listOf("Test Games, signed in as Ann, last saved Oct 15, 2026 14:05"),
            compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun `never saved - or signed out - shows no save time`() {
        show(FakeGameServices(GameServicesState.SignedOut()), lastSavedEpochMillis = 1_792_073_100_000L)
        compose.onNodeWithText("Last saved", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf("Test Games, Not signed in"), compose.onNode(isHeading()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `no service draws nothing`() {
        show(NoGameServices)
        compose.onNodeWithText("Not signed in").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun `signed out offers sign in and nothing else`() {
        val services = FakeGameServices(GameServicesState.SignedOut())
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
        var gold = Color.Unspecified
        val longName = "A very long gamer name indeed"
        compose.setContent {
            DiceFiveTheme {
                gold = MaterialTheme.colorScheme.primary
                OnlineAccountCard(FakeGameServices(GameServicesState.SignedIn(OnlineAccount(longName, null, "key"))))
            }
        }
        // On screen, the name alone - in gold, a size up from the lines under it (14sp): "signed in as" is spoken only.
        compose.onNodeWithText("Signed in as", substring = true, useUnmergedTree = true).assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longName, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(15.sp, style.fontSize)
        assertEquals(gold, style.color)
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
    fun `signing in keeps Sign in - greyed and disabled - and offers no Sign out`() {
        show(FakeGameServices(GameServicesState.SigningIn))
        compose.onNodeWithText("Signing in...", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun `signed in - Sign in stays disabled until the sign-in's sync is done - and only then is Sign out offered`() {
        var synced by mutableStateOf(false)
        val services = FakeGameServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "key")))
        compose.setContent { DiceFiveTheme { OnlineAccountCard(services, signInSynced = synced) } }
        compose.onNodeWithText("Ann", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sign in").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
        synced = true
        compose.onNodeWithText("Sign in").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertIsEnabled()
        assertEquals(0, services.signInCalls)
    }
}
