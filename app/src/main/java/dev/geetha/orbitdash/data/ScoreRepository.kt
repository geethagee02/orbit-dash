package dev.geetha.orbitdash.data

import com.squareup.moshi.JsonDataException
import dev.geetha.orbitdash.domain.OrbitRules
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val score: Int,
    val dodges: Int,
)

sealed interface BoardResult {
    data class Online(val rows: List<LeaderboardEntry>) : BoardResult

    data object Offline : BoardResult
}

sealed interface PostResult {
    data class Posted(val rank: Int) : PostResult

    data object InvalidName : PostResult

    data object Rejected : PostResult

    data object Unreachable : PostResult
}

interface ScoreRepository {
    fun bestScore(): Flow<Int>

    fun pilotName(): Flow<String>

    suspend fun recordRun(score: Int, dodges: Int)

    suspend fun leaderboard(): BoardResult

    suspend fun postRun(name: String, score: Int, dodges: Int): PostResult
}

@Singleton
class ScoreRepositoryImpl @Inject constructor(
    private val api: ScoreApi,
    private val bestScores: BestScoreDao,
    private val profiles: ProfileDao,
) : ScoreRepository {
    override fun bestScore(): Flow<Int> = bestScores.observe().map { it?.score ?: 0 }

    override fun pilotName(): Flow<String> = profiles.observeName().map { stored ->
        stored?.let(OrbitRules::canonicalName) ?: "Geetha"
    }

    override suspend fun recordRun(score: Int, dodges: Int) {
        val stored = bestScores.current()?.score ?: 0
        if (!OrbitRules.isNewBest(score, stored)) return
        bestScores.upsert(
            BestScoreEntity(
                score = score,
                dodges = dodges,
                atMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun leaderboard(): BoardResult {
        return try {
            val body = withContext(Dispatchers.IO) { api.leaderboard(LIMIT) }
            BoardResult.Online(
                body.scores.map { row ->
                    LeaderboardEntry(
                        rank = row.rank,
                        name = row.name,
                        score = row.score,
                        dodges = row.dodges,
                    )
                },
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: IOException) {
            BoardResult.Offline
        } catch (error: HttpException) {
            BoardResult.Offline
        } catch (error: JsonDataException) {
            BoardResult.Offline
        }
    }

    override suspend fun postRun(name: String, score: Int, dodges: Int): PostResult {
        val pilot = OrbitRules.canonicalName(name) ?: return PostResult.InvalidName
        return try {
            val envelope = withContext(Dispatchers.IO) {
                api.submit(SubmitRequest(name = pilot, score = score, dodges = dodges))
            }
            profiles.upsert(ProfileEntity(name = pilot))
            PostResult.Posted(envelope.score.rank)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: IOException) {
            PostResult.Unreachable
        } catch (error: HttpException) {
            if (error.code() in 400..499) PostResult.Rejected else PostResult.Unreachable
        } catch (error: JsonDataException) {
            PostResult.Unreachable
        }
    }

    private companion object {
        const val LIMIT = 20
    }
}
