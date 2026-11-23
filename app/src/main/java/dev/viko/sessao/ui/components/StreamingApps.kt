package dev.viko.sessao.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.viko.sessao.domain.Provider

/**
 * Provedor do TMDB para o app do serviço no Android. O TMDB não manda link direto para o título,
 * então o app abre na tela inicial dele; sem o app, abre a página de "Onde assistir" (dados do JustWatch).
 */
object StreamingApps {
    private val packages = listOf(
        "netflix" to "com.netflix.mediaclient",
        "amazon prime video" to "com.amazon.avod.thirdpartyclient",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "disney plus" to "com.disney.disneyplus",
        "max" to "com.wbd.stream",
        "hbo max" to "com.wbd.stream",
        "globoplay" to "com.globo.globotv",
        "apple tv plus" to "com.apple.atve.android.appletv",
        "apple tv+" to "com.apple.atve.android.appletv",
        "paramount plus" to "com.cbs.app",
        "paramount+" to "com.cbs.app",
        "crunchyroll" to "com.crunchyroll.crunchyroid",
        "mubi" to "com.mubi",
        "pluto tv" to "tv.pluto.android",
        "youtube" to "com.google.android.youtube",
        "google play movies" to "com.google.android.videos",
    )

    fun packageFor(p: Provider): String? {
        val n = p.name.trim().lowercase()
        return packages.firstOrNull { (k, _) -> n == k }?.second
            ?: packages.firstOrNull { (k, _) -> n.startsWith(k) }?.second
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun open(context: Context, p: Provider, fallbackUrl: String?) {
        fun start(i: Intent) = context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val pkg = packageFor(p)
        if (pkg != null && isInstalled(context, pkg)) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { start(it) }; return }
        }
        fallbackUrl?.let { url -> try { start(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) {} }
    }

    /** Abre o filme no Letterboxd (no app, se estiver instalado). */
    fun openLetterboxd(context: Context, tmdbId: Int) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://letterboxd.com/tmdb/$tmdbId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(Intent(view).setPackage("com.letterboxd.letterboxd")); return } catch (_: ActivityNotFoundException) {}
        runCatching { context.startActivity(view) }
    }
}
