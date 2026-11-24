package dev.geetha.orbitdash.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.geetha.orbitdash.BuildConfig
import dev.geetha.orbitdash.data.OrbitDatabase
import dev.geetha.orbitdash.data.ScoreApi
import dev.geetha.orbitdash.data.ScoreRepository
import dev.geetha.orbitdash.data.ScoreRepositoryImpl
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object OrbitModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OrbitDatabase {
        return Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit-dash.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT INTO profile (id, name) VALUES (1, 'Geetha')")
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun api(): ScoreApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ScoreApi::class.java)
    }

    @Provides
    @Singleton
    fun repository(api: ScoreApi, database: OrbitDatabase): ScoreRepository {
        return ScoreRepositoryImpl(api, database.bestScores(), database.profiles())
    }
}
