package net.zodac.yahtzee.ui.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zodac.yahtzee.model.GameState

/**
 * Holds and exposes [GameState] for [GameScreen]. Roll/hold/score actions
 * will be implemented once game rules and turn flow are finalized.
 */
class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
}
