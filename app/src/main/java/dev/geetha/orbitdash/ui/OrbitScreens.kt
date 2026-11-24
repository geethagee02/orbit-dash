package dev.geetha.orbitdash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.geetha.orbitdash.R
import dev.geetha.orbitdash.data.LeaderboardEntry
import dev.geetha.orbitdash.domain.OrbitRules
import java.text.NumberFormat
import java.util.Locale

private enum class AppScreen { Play, Board }

private val groupedFormat = NumberFormat.getIntegerInstance(Locale.US)

private fun grouped(value: Int): String = groupedFormat.format(value)

@Composable
fun OrbitAppScreen() {
    OrbitTheme {
        val game: GameViewModel = hiltViewModel()
        val board: LeaderboardViewModel = hiltViewModel()
        val gameState by game.state.collectAsStateWithLifecycle()
        val boardState by board.ui.collectAsStateWithLifecycle()
        var screen by remember { mutableStateOf(AppScreen.Play) }
        if (screen == AppScreen.Play) {
            PlayScreen(
                state = gameState,
                game = game,
                onBoard = {
                    board.refresh()
                    screen = AppScreen.Board
                },
            )
        } else {
            BoardScreen(
                state = boardState,
                onBack = { screen = AppScreen.Play },
                onRefresh = board::refresh,
            )
        }
    }
}

@Composable
private fun PlayScreen(
    state: GameUiState,
    game: GameViewModel,
    onBoard: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, game) {
        game.resumeClock()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> game.pauseClock()
                Lifecycle.Event.ON_START -> game.resumeClock()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            game.pauseClock()
        }
    }

    val haptic = LocalHapticFeedback.current
    var seen by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        if (seen == Phase.Playing && state.phase == Phase.Lost) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        seen = state.phase
    }

    val playfield = stringResource(R.string.playfield)
    Box(Modifier.fillMaxSize().background(Ink)) {
        OrbitField(
            world = state.world,
            stars = state.stars,
            trail = state.trail,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.phase == Phase.Lost) {
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)))
        }
        if (state.phase == Phase.Playing) {
            Column(Modifier.fillMaxSize()) {
                Hud(state)
                Spacer(Modifier.weight(1f))
                Hint(state.world.direction)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = playfield }
                    .pointerInput(game) {
                        detectTapGestures {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            game.flip()
                        }
                    },
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
                Spacer(Modifier.weight(1f))
                if (state.phase == Phase.Ready) {
                    ReadySheet(best = state.best, onPlay = game::play, onBoard = onBoard)
                } else {
                    LostSheet(state = state, game = game, onBoard = onBoard)
                }
            }
        }
    }
}

@Composable
private fun Hud(state: GameUiState) {
    val ahead = state.world.score > state.best
    Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.score_label), style = MaterialTheme.typography.labelSmall)
                Text(
                    text = grouped(state.world.score),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (ahead) Teal else Paper,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.best_label), style = MaterialTheme.typography.labelSmall)
                Text(
                    text = grouped(state.best),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    color = Paper,
                )
            }
        }
        Text(
            text = stringResource(
                R.string.streak_line,
                state.world.streak,
                OrbitRules.angularSpeed(state.world.dodges) / OrbitRules.angularSpeed(0),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Mute,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            Modifier
                .padding(top = 10.dp)
                .width(128.dp)
                .height(3.dp)
                .background(Mute.copy(alpha = 0.35f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(128.dp * OrbitRules.pace(state.world.dodges).coerceIn(0f, 1f))
                    .background(Teal),
            )
        }
    }
}

@Composable
private fun Hint(direction: Int) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.tap_hint), style = MaterialTheme.typography.labelSmall, color = Paper)
        Text(
            text = stringResource(if (direction < 0) R.string.direction_cw else R.string.direction_ccw),
            style = MaterialTheme.typography.bodyMedium,
            color = Amber,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ReadySheet(best: Int, onPlay: () -> Unit, onBoard: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.96f), Ink)))
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 8.dp),
    ) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.labelSmall, color = Teal)
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = Mute,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (best > 0) {
            Text(
                text = stringResource(R.string.best_value, best),
                style = MaterialTheme.typography.bodyMedium,
                color = Amber,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        AmberButton(stringResource(R.string.launch), onPlay)
        TextButton(onClick = onBoard, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.board), color = Teal)
        }
        Text(
            text = stringResource(R.string.credit),
            style = MaterialTheme.typography.bodyMedium,
            color = Mute,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LostSheet(state: GameUiState, game: GameViewModel, onBoard: () -> Unit) {
    val personal = state.world.score > 0 && state.world.score >= state.best
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(InkRaised, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text(stringResource(R.string.lost_title), style = MaterialTheme.typography.labelSmall, color = Coral)
        Text(
            text = grouped(state.world.score),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = if (personal) {
                stringResource(R.string.personal_best)
            } else {
                stringResource(R.string.best_value, state.best)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (personal) Teal else Mute,
            modifier = Modifier.padding(top = 2.dp),
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = game::onName,
            singleLine = true,
            label = { Text(stringResource(R.string.pilot_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(16.dp))
        AmberButton(stringResource(R.string.again), game::play)
        OutlinedButton(
            onClick = game::submit,
            enabled = !state.posting && state.world.score > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = stringResource(if (state.posting) R.string.posting else R.string.post),
                color = if (state.posting) Mute else Teal,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(onClick = onBoard, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.board), color = Paper)
        }
        NoticeLine(state.notice)
    }
}

@Composable
private fun NoticeLine(notice: Notice) {
    val text = when (notice) {
        Notice.None -> return
        is Notice.Posted -> stringResource(R.string.posted, notice.rank)
        Notice.InvalidName -> stringResource(R.string.invalid_name)
        Notice.Rejected -> stringResource(R.string.rejected)
        Notice.Offline -> stringResource(R.string.offline_post)
        Notice.SaveFailed -> stringResource(R.string.save_failed)
    }
    val color = when (notice) {
        is Notice.Posted -> Teal
        Notice.Offline -> Amber
        else -> Coral
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AmberButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal,
    unfocusedBorderColor = Mute,
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    cursorColor = Amber,
    focusedLabelColor = Teal,
    unfocusedLabelColor = Mute,
)

@Composable
private fun BoardScreen(
    state: LeaderboardViewModel.Ui,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = Teal) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !state.loading) {
                Text(stringResource(R.string.refresh), color = Amber)
            }
        }
        Text(
            text = stringResource(R.string.board_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (state.best > 0) {
            Text(
                text = stringResource(R.string.phone_best, state.best),
                style = MaterialTheme.typography.bodyMedium,
                color = Mute,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                state.loading && state.rows.isEmpty() -> CircularProgressIndicator(
                    color = Teal,
                    modifier = Modifier.size(28.dp),
                )
                state.rows.isEmpty() && state.offline -> Text(
                    text = stringResource(R.string.offline_board),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                state.rows.isEmpty() -> Text(
                    text = stringResource(R.string.empty_board),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mute,
                )
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    items(state.rows, key = { it.rank }) { row ->
                        BoardRow(row)
                    }
                    if (state.offline) {
                        item {
                            Text(
                                text = stringResource(R.string.offline_board),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Amber,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(row: LeaderboardEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = if (row.rank == 1) Amber else Mute,
                modifier = Modifier.width(36.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge, color = Paper)
                Text(
                    text = stringResource(R.string.dodges, row.dodges),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mute,
                )
            }
            Text(
                text = grouped(row.score),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Mute.copy(alpha = 0.2f)),
        )
    }
}
