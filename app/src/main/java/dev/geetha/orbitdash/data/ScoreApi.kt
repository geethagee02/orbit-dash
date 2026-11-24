package dev.geetha.orbitdash.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ScoreApi {
    @GET("v1/scores")
    suspend fun leaderboard(@Query("limit") limit: Int): BoardEnvelope

    @POST("v1/scores")
    suspend fun submit(@Body body: SubmitRequest): ScoreEnvelope
}

data class SubmitRequest(
    val name: String,
    val score: Int,
    val dodges: Int,
)

data class ScoreDto(
    val id: Long,
    val name: String,
    val score: Int,
    val dodges: Int,
    val createdAt: String,
    val rank: Int,
)

data class ScoreEnvelope(val score: ScoreDto)

data class BoardEnvelope(val scores: List<ScoreDto>)
