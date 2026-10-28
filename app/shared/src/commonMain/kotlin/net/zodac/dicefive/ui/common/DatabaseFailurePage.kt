package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.common_database_failure_advice
import net.zodac.dicefive.resources.common_database_failure_body
import net.zodac.dicefive.resources.common_database_failure_title

/**
 * Shown in place of every page while the scores database can't be opened (`AppContainer.databaseFailure`): what has
 * happened and what to do about it, then [details], the error's stack trace, always shown - see [FailurePage].
 */
@Composable
fun DatabaseFailurePage(details: String) {
    FailurePage(
        title = stringResource(Res.string.common_database_failure_title),
        body = stringResource(Res.string.common_database_failure_body, stringResource(Res.string.app_name)),
        advice = stringResource(Res.string.common_database_failure_advice),
    ) {
        FailureDetails(details)
    }
}
