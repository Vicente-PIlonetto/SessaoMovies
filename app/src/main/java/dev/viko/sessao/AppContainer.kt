package dev.viko.sessao

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.viko.sessao.data.api.Tmdb
import dev.viko.sessao.data.backup.BackupManager
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.data.letterboxd.LetterboxdRepository
import dev.viko.sessao.data.repo.ListRepository
import dev.viko.sessao.data.repo.TitleRepository
import dev.viko.sessao.data.settings.SettingsRepository
import dev.viko.sessao.data.sync.RefreshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Dependências do app, criadas uma vez. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = AppDatabase.build(context)
    val settings = SettingsRepository(context)

    /** Chave das Configurações, se houver; senão a embutida no APK. */
    @Volatile private var keyOverride: String = ""
    val tmdb = Tmdb { keyOverride.ifBlank { BuildConfig.TMDB_API_KEY } }

    /** Pôsteres com cache em disco: a lista abre sem internet. */
    val images: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { tmdb.http })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("posters")).maxSizeBytes(250L * 1024 * 1024).build() }
        .crossfade(true)
        .build()

    val lists = ListRepository(db)
    val titles = TitleRepository(context, db, tmdb, images)
    val letterboxd = LetterboxdRepository(context, db, titles, settings, tmdb.http)
    val trakt = dev.viko.sessao.data.trakt.TraktRepository(context, db, lists, titles, BuildConfig.TRAKT_CLIENT_ID)
    val social = dev.viko.sessao.data.trakt.SocialRepository(trakt, titles)
    val refresh = RefreshManager(context, db, lists, titles, letterboxd, settings, trakt)
    val backup = BackupManager(context, db, settings)

    init {
        appScope.launch { settings.settings.collect { keyOverride = it.tmdbKey } }
    }
}
