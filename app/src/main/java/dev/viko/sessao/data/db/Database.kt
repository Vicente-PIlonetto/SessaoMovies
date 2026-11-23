package dev.viko.sessao.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import dev.viko.sessao.domain.AiredEpisode
import dev.viko.sessao.domain.EpisodeInfo
import dev.viko.sessao.domain.LetterboxdSeen
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.NotificationLog
import dev.viko.sessao.domain.SeasonInfo
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.WatchProviders
import dev.viko.sessao.domain.WatchedEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class Converters {
    @TypeConverter fun strings(v: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), v)
    @TypeConverter fun toStrings(v: String): List<String> = runCatching { json.decodeFromString(ListSerializer(String.serializer()), v) }.getOrDefault(emptyList())

    @TypeConverter fun ints(v: List<Int>): String = v.joinToString(",")
    @TypeConverter fun toInts(v: String): List<Int> = v.split(',').mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter fun seasons(v: List<SeasonInfo>): String = json.encodeToString(ListSerializer(SeasonInfo.serializer()), v)
    @TypeConverter fun toSeasons(v: String): List<SeasonInfo> = runCatching { json.decodeFromString(ListSerializer(SeasonInfo.serializer()), v) }.getOrDefault(emptyList())

    @TypeConverter fun aired(v: AiredEpisode?): String? = v?.let { json.encodeToString(AiredEpisode.serializer(), it) }
    @TypeConverter fun toAired(v: String?): AiredEpisode? = v?.let { runCatching { json.decodeFromString(AiredEpisode.serializer(), it) }.getOrNull() }

    @TypeConverter fun providers(v: WatchProviders?): String? = v?.let { json.encodeToString(WatchProviders.serializer(), it) }
    @TypeConverter fun toProviders(v: String?): WatchProviders? = v?.let { runCatching { json.decodeFromString(WatchProviders.serializer(), it) }.getOrNull() }
}

@Dao
interface TitleDao {
    @Query("SELECT * FROM titles") fun observeAll(): Flow<List<Title>>
    @Query("SELECT * FROM titles WHERE type = :type AND id = :id") fun observe(type: MediaType, id: Int): Flow<Title?>
    @Query("SELECT * FROM titles WHERE type = :type AND id = :id") suspend fun get(type: MediaType, id: Int): Title?
    @Query("SELECT * FROM titles") suspend fun all(): List<Title>
    @Upsert suspend fun upsert(t: Title)
    @Query("UPDATE titles SET posterColor = :color WHERE type = :type AND id = :id") suspend fun setColor(type: MediaType, id: Int, color: Int)
    /** Títulos fora da lista (abertos só para consulta) somem do cache depois de um tempo. */
    @Query("DELETE FROM titles WHERE updatedAt < :before AND NOT EXISTS (SELECT 1 FROM entries e WHERE e.type = titles.type AND e.id = titles.id)")
    suspend fun pruneUnlisted(before: Long)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries") fun observeAll(): Flow<List<ListEntry>>
    @Query("SELECT * FROM entries WHERE type = :type AND id = :id") fun observe(type: MediaType, id: Int): Flow<ListEntry?>
    @Query("SELECT * FROM entries WHERE type = :type AND id = :id") suspend fun get(type: MediaType, id: Int): ListEntry?
    @Query("SELECT * FROM entries") suspend fun all(): List<ListEntry>
    @Upsert suspend fun upsert(e: ListEntry)
    @Delete suspend fun delete(e: ListEntry)
}

@Dao
interface WatchedDao {
    @Query("SELECT * FROM watched") fun observeAll(): Flow<List<WatchedEpisode>>
    @Query("SELECT * FROM watched WHERE showId = :showId AND cycle = :cycle") fun observe(showId: Int, cycle: Int): Flow<List<WatchedEpisode>>
    @Query("SELECT * FROM watched WHERE showId = :showId AND cycle = :cycle") suspend fun get(showId: Int, cycle: Int): List<WatchedEpisode>
    @Query("SELECT * FROM watched") suspend fun all(): List<WatchedEpisode>
    @Upsert suspend fun upsert(list: List<WatchedEpisode>)
    @Query("DELETE FROM watched WHERE showId = :showId AND cycle = :cycle AND season = :season AND episode IN (:episodes)")
    suspend fun delete(showId: Int, cycle: Int, season: Int, episodes: List<Int>)
    @Query("DELETE FROM watched WHERE showId = :showId") suspend fun deleteShow(showId: Int)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY season, episode") fun observeShow(showId: Int): Flow<List<EpisodeInfo>>
    @Query("SELECT * FROM episodes WHERE airDate BETWEEN :from AND :to ORDER BY airDate") fun observeRange(from: String, to: String): Flow<List<EpisodeInfo>>
    @Upsert suspend fun upsert(list: List<EpisodeInfo>)
}

@Dao
interface MiscDao {
    @Query("SELECT guid FROM letterboxd_seen") suspend fun seenGuids(): List<String>
    @Upsert suspend fun markSeen(list: List<LetterboxdSeen>)

    @Query("SELECT * FROM notifications ORDER BY at DESC LIMIT 200") fun observeNotifications(): Flow<List<NotificationLog>>
    @Upsert suspend fun log(n: NotificationLog)
    @Query("SELECT COUNT(*) FROM notifications WHERE at >= :since") suspend fun countSince(since: Long): Int
}

@Database(
    entities = [Title::class, ListEntry::class, WatchedEpisode::class, EpisodeInfo::class, LetterboxdSeen::class, NotificationLog::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun titles(): TitleDao
    abstract fun entries(): EntryDao
    abstract fun watched(): WatchedDao
    abstract fun episodes(): EpisodeDao
    abstract fun misc(): MiscDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sessao.db").build()
    }
}
