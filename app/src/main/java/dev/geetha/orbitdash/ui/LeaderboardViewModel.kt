package dev.geetha.orbitdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.geetha.orbitdash.data.BoardResult
import dev.geetha.orbitdash.data.LeaderboardEntry
import dev.geetha.orbitdash.data.ScoreRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val scores: ScoreRepository,
) : ViewModel() {
    data class Ui(
        val rows: List<LeaderboardEntry> = emptyList(),
        val loading: Boolean = true,
        val offline: Boolean = false,
        val best: Int = 0,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var load: Job? = null

    init {
        viewModelScope.launch {
            scores.bestScore().collect { best ->
                _ui.update { it.copy(best = best) }
            }
        }
        refresh()
    }

    fun refresh() {
        load?.cancel()
        load = viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            when (val result = scores.leaderboard()) {
                is BoardResult.Online -> _ui.update {
                    it.copy(rows = result.rows, loading = false, offline = false)
                }
                BoardResult.Offline -> _ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }
}
