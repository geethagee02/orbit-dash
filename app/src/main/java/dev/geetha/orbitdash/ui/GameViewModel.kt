package dev.geetha.orbitdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.geetha.orbitdash.data.PostResult
import dev.geetha.orbitdash.data.ScoreRepository
import dev.geetha.orbitdash.domain.OrbitRules
import dev.geetha.orbitdash.domain.World
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

enum class Phase { Ready, Playing, Lost }

sealed interface Notice {
    data object None : Notice

    data class Posted(val rank: Int) : Notice

    data object InvalidName : Notice

    data object Rejected : Notice

    data object Offline : Notice

    data object SaveFailed : Notice
}

data class GameUiState(
    val phase: Phase = Phase.Ready,
    val world: World = World(),
    val trail: List<Float> = emptyList(),
    val stars: List<Star> = starfield(),
    val best: Int = 0,
    val name: String = "Geetha",
    val nameDirty: Boolean = false,
    val posting: Boolean = false,
    val notice: Notice = Notice.None,
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val scores: ScoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val flips = AtomicInteger(0)
    private val postingGate = AtomicBoolean(false)

    @Volatile
    private var clockOn: Boolean = true

    init {
        viewModelScope.launch {
            scores.bestScore().collect { best ->
                _state.update { it.copy(best = best) }
            }
        }
        viewModelScope.launch {
            scores.pilotName().collect { stored ->
                _state.update { state ->
                    if (state.nameDirty) state else state.copy(name = stored)
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) { clock() }
    }

    fun play() {
        flips.set(0)
        postingGate.set(false)
        val seed = System.nanoTime().let { if (it == 0L) 1L else it }
        _state.update {
            it.copy(
                phase = Phase.Playing,
                world = World(rng = seed),
                trail = listOf(0f),
                notice = Notice.None,
                posting = false,
            )
        }
    }

    fun flip() {
        if (_state.value.phase == Phase.Playing && clockOn) flips.incrementAndGet()
    }

    fun onName(value: String) {
        _state.update { it.copy(name = value.take(16), nameDirty = true, notice = Notice.None) }
    }

    fun pauseClock() {
        clockOn = false
    }

    fun resumeClock() {
        clockOn = true
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.phase != Phase.Lost || snapshot.world.score <= 0) return
        if (!postingGate.compareAndSet(false, true)) return
        viewModelScope.launch {
            _state.update { it.copy(posting = true, notice = Notice.None) }
            val result = try {
                scores.postRun(snapshot.name, snapshot.world.score, snapshot.world.dodges)
            } catch (cancel: CancellationException) {
                postingGate.set(false)
                throw cancel
            } catch (error: Exception) {
                PostResult.Unreachable
            }
            _state.update {
                it.copy(
                    posting = false,
                    notice = when (result) {
                        is PostResult.Posted -> Notice.Posted(result.rank)
                        PostResult.InvalidName -> Notice.InvalidName
                        PostResult.Rejected -> Notice.Rejected
                        PostResult.Unreachable -> Notice.Offline
                    },
                )
            }
            postingGate.set(false)
        }
    }

    private suspend fun clock() {
        var last = System.nanoTime()
        while (true) {
            val running = clockOn && _state.value.phase == Phase.Playing
            if (!running) {
                delay(40)
                last = System.nanoTime()
                continue
            }
            val now = System.nanoTime()
            val dt = ((now - last).coerceAtLeast(0L) / 1_000_000_000f).coerceAtMost(0.25f)
            last = now
            val flip = flips.getAndSet(0) % 2 == 1
            var died = false
            val after = _state.updateAndGet { state ->
                if (state.phase != Phase.Playing) {
                    died = false
                    state
                } else {
                    val next = OrbitRules.step(state.world, flip, dt)
                    val justDied = state.world.alive && !next.alive
                    died = justDied
                    state.copy(
                        phase = if (justDied) Phase.Lost else Phase.Playing,
                        world = next,
                        trail = (listOf(next.shipAngle) + state.trail).take(TRAIL),
                    )
                }
            }
            if (died) {
                try {
                    scores.recordRun(after.world.score, after.world.dodges)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    _state.update { current ->
                        if (current.notice is Notice.None) current.copy(notice = Notice.SaveFailed) else current
                    }
                }
            }
            delay(16)
        }
    }

    private companion object {
        const val TRAIL = 28
    }
}
