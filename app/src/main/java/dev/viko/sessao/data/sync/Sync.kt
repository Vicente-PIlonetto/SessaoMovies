package dev.viko.sessao.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.viko.sessao.App
import dev.viko.sessao.MainActivity
import dev.viko.sessao.R
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.data.letterboxd.LetterboxdRepository
import dev.viko.sessao.data.repo.ListRepository
import dev.viko.sessao.data.repo.TitleRepository
import dev.viko.sessao.data.repo.now
import dev.viko.sessao.data.settings.SettingsRepository
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.NotificationLog
import dev.viko.sessao.domain.Progress
import dev.viko.sessao.domain.ProviderRules
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Um aviso a enviar ao fim da atualização. */
data class Notice(val channel: String, val title: String, val text: String, val key: TitleKey)

/** Resultado de uma atualização (para a tela mostrar o erro, se houver). */
data class RefreshResult(val updated: Int, val failed: Int, val error: String? = null)

/**
 * Atualização da lista: episódios novos e datas das séries em andamento, provedores do que não terminei,
 * feed do Letterboxd. Roda uma vez por dia (WorkManager) e quando puxo a tela do Início.
 */
class RefreshManager(
    private val context: Context,
    private val db: AppDatabase,
    private val lists: ListRepository,
    private val titles: TitleRepository,
    private val letterboxd: LetterboxdRepository,
    private val settings: SettingsRepository,
    private val trakt: dev.viko.sessao.data.trakt.TraktRepository,
) {
    suspend fun refreshAll(notify: Boolean): RefreshResult {
        val s = settings.current()
        val entries = db.entries().all()
        val notices = mutableListOf<Notice>()
        var updated = 0
        var failed = 0
        var firstError: String? = null
        val mine = s.myServiceIds

        for (e in entries) {
            val key = e.key
            val before = db.titles().get(key.type, key.id)
            // Título sem dados completos (veio de backup, do Letterboxd ou foi adicionado offline).
            val incomplete = before == null || before.updatedAt == 0L
            val showRefresh = key.type == MediaType.TV && e.status.active
            val providersRefresh = e.status != WatchStatus.COMPLETED && e.status != WatchStatus.DROPPED
            if (!showRefresh && !providersRefresh && !incomplete) continue
            try {
                if (incomplete && !showRefresh) {
                    titles.refresh(key)
                } else if (showRefresh) {
                    // Temporada do próximo episódio a ver também, para o "+1" saber a contagem.
                    val watched = db.watched().get(key.id, e.cycle).map { it.ep }
                    val nextSeason = before?.let { Progress.next(watched, it.seasons)?.season }
                    val after = titles.refresh(key, setOfNotNull(nextSeason))
                    lists.afterRefresh(key)
                    val oldLast = before?.lastAired?.ep
                    val newLast = after.lastAired?.ep
                    if (oldLast != null && newLast != null && newLast > oldLast && e.status != WatchStatus.PAUSED) {
                        notices += Notice(CH_EPISODES, after.name, t("{0} · {1} disponível", after.name, newLast.label), key)
                    }
                    if ((before?.providersUpdatedAt ?: 0) > 0) {
                        ProviderRules.newInMyServices(before?.providers, after.providers, mine).firstOrNull()?.let {
                            notices += Notice(CH_PROVIDERS, after.name, t("{0} · agora na {1}", after.name, it.name), key)
                        }
                    }
                } else {
                    val (old, fresh) = titles.refreshProviders(key)
                    ProviderRules.newInMyServices(old, fresh, mine).firstOrNull()?.let {
                        val name = before?.name.orEmpty()
                        notices += Notice(CH_PROVIDERS, name, t("{0} · agora na {1}", name, it.name), key)
                    }
                }
                updated++
            } catch (ex: Exception) {
                failed++
                if (firstError == null) firstError = ex.message
                if (ex is dev.viko.sessao.data.api.TmdbError.NoKey || ex is dev.viko.sessao.data.api.TmdbError.Offline || ex is dev.viko.sessao.data.api.TmdbError.BadKey) break
            }
        }

        // Trakt antes do Letterboxd: o que vier de lá já entra na lista.
        if (trakt.session.value != null) {
            runCatching { trakt.sync() }.onFailure { if (firstError == null) firstError = it.message }
        }
        if (s.letterboxdSync && s.letterboxdUser.isNotBlank()) {
            runCatching { letterboxd.syncFeed() }.onFailure { if (firstError == null) firstError = it.message }
        }
        runCatching { titles.pruneCache() }
        settings.setLastRefresh(now())

        if (notify) {
            val wanted = notices.filter { (it.channel == CH_EPISODES && s.notifyEpisodes) || (it.channel == CH_PROVIDERS && s.notifyProviders) }
            Notifier.post(context, db, settings, wanted)
        }
        return RefreshResult(updated, failed, firstError)
    }

    companion object {
        const val CH_EPISODES = "episodes"
        const val CH_PROVIDERS = "providers"
    }
}

object Notifier {
    private const val GROUP_PREFIX = "sessao-"

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(RefreshManager.CH_EPISODES, t("Episódios novos"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(RefreshManager.CH_PROVIDERS, t("Chegou num serviço meu"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /**
     * Posta os avisos. Os do mesmo dia ficam num grupo só, com um resumo:
     * a notificação de resumo usa o mesmo id durante o dia inteiro.
     */
    suspend fun post(ctx: Context, db: AppDatabase, settings: SettingsRepository, notices: List<Notice>) {
        if (notices.isEmpty()) return
        notices.forEach { db.misc().log(NotificationLog(at = now(), title = it.title, text = it.text, type = it.key.type, tmdbId = it.key.id)) }
        if (!hasPermission(ctx)) return
        val day = LocalDate.now().toString()
        val group = GROUP_PREFIX + day
        val nm = NotificationManagerCompat.from(ctx)
        notices.forEach { n ->
            val id = (n.key.toString() + n.text).hashCode()
            val b = NotificationCompat.Builder(ctx, n.channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(n.text)
                .setContentIntent(open(ctx, n.key, id))
                .setAutoCancel(true)
                .setGroup(group)
            runCatching { nm.notify(id, b.build()) }
        }
        val summaryId = day.hashCode()
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = db.misc().countSince(start)
        val summary = NotificationCompat.Builder(ctx, notices.first().channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t("Sessão"))
            .setContentText(if (today == 1) notices.first().text else t("{0} novidades hoje", today))
            .setStyle(NotificationCompat.InboxStyle().also { st -> notices.take(6).forEach { st.addLine(it.text) } })
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(open(ctx, null, summaryId))
        runCatching { nm.notify(summaryId, summary.build()) }
        settings.setNotifiedDay(day)
    }

    private fun open(ctx: Context, key: TitleKey?, code: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (key != null) i.putExtra(MainActivity.EXTRA_TITLE, key.toString())
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as App).container
        val r = c.refresh.refreshAll(notify = true)
        return if (r.updated == 0 && r.failed > 0) Result.retry() else Result.success()
    }
}

object WorkScheduler {
    private const val DAILY = "daily-refresh"

    fun scheduleDaily(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Atualização logo depois de importar um backup (baixa os dados do TMDB de novo). */
    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, req)
    }
}
