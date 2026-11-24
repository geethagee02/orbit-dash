package dev.geetha.orbitdash.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "best_score")
data class BestScoreEntity(
    @PrimaryKey val id: Int = 1,
    val score: Int,
    val dodges: Int,
    val atMillis: Long,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
)

@Dao
interface BestScoreDao {
    @Query("SELECT * FROM best_score WHERE id = 1")
    fun observe(): Flow<BestScoreEntity?>

    @Query("SELECT * FROM best_score WHERE id = 1")
    suspend fun current(): BestScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BestScoreEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT name FROM profile WHERE id = 1")
    fun observeName(): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ProfileEntity)
}

@Database(
    entities = [BestScoreEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun bestScores(): BestScoreDao

    abstract fun profiles(): ProfileDao
}
