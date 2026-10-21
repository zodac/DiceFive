package net.zodac.dicefive.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.online.ImportStep
import net.zodac.dicefive.ui.settings.ProgressFilesStatus.Finished
import net.zodac.dicefive.ui.settings.ProgressFilesStatus.Importing

/** How the progress files card catches up with an import, a step at a time ([nextShownStatus]). */
class PacedStatusTest {

    /** Everything the card shows on its way from [shown] to [target], in order. */
    private fun walk(shown: ProgressFilesStatus?, target: ProgressFilesStatus?): List<ProgressFilesStatus?> {
        val walked = mutableListOf<ProgressFilesStatus?>()
        var current = shown
        while (true) {
            current = (nextShownStatus(current, target) ?: return walked).status
            walked += current
        }
    }

    private val allSteps = ImportStep.entries.map(::Importing)

    @Test
    fun `an import the card never saw run still walks every step - then says it's done`() {
        assertEquals(allSteps + Finished(ProgressOutcome.IMPORTED), walk(null, Finished(ProgressOutcome.IMPORTED)))
        // After another one's outcome, as a new attempt's clearing can be missed.
        assertEquals(allSteps + Finished(ProgressOutcome.IMPORTED), walk(Finished(ProgressOutcome.EXPORTED), Finished(ProgressOutcome.IMPORTED)))
    }

    @Test
    fun `the card walks only as far as the import has got`() {
        assertEquals(listOf(Importing(ImportStep.OPENING), Importing(ImportStep.SETTINGS)), walk(null, Importing(ImportStep.SETTINGS)))
        assertEquals(listOf(Importing(ImportStep.SCORES)), walk(Importing(ImportStep.ACHIEVEMENTS), Importing(ImportStep.SCORES)))
        assertEquals(emptyList(), walk(Importing(ImportStep.SCORES), Importing(ImportStep.GAME)))
    }

    @Test
    fun `a file it couldn't open is said straight after the step showing`() {
        assertEquals(listOf(Finished(ProgressOutcome.NOT_A_PROGRESS_FILE)), walk(Importing(ImportStep.OPENING), Finished(ProgressOutcome.NOT_A_PROGRESS_FILE)))
        assertEquals(listOf(Finished(ProgressOutcome.IMPORT_FAILED)), walk(null, Finished(ProgressOutcome.IMPORT_FAILED)))
    }

    @Test
    fun `an export's outcome shows at once - and a new attempt clears the last`() {
        assertEquals(listOf(Finished(ProgressOutcome.EXPORTED)), walk(null, Finished(ProgressOutcome.EXPORTED)))
        assertEquals(listOf(null), walk(Finished(ProgressOutcome.EXPORTED), null))
        assertEquals(emptyList(), walk(Finished(ProgressOutcome.IMPORTED), Finished(ProgressOutcome.IMPORTED)))
    }

    @Test
    fun `the steps share out at least a second between them - however many there are`() {
        val total = IMPORT_STEP_MILLIS * ImportStep.entries.size
        assertTrue(total in IMPORT_SHOWN_MILLIS until IMPORT_SHOWN_MILLIS + ImportStep.entries.size, "$total")
    }
}
