package dev.viko.sessao.i18n

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Idiomas da interface. O português do Brasil é o texto original (as chaves). */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    PT_BR("pt-BR", "Português (Brasil)"),
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    IT("it", "Italiano"),
    RU("ru", "Русский"),
    JA("ja", "日本語"),
    ZH("zh-CN", "中文（简体）"),
}

/**
 * Tradução por chave: a chave é o texto em português. Sem tradução, mostra o português.
 * O idioma é lido na abertura do app; trocar reinicia o app (os textos são montados uma vez).
 */
object I18n {
    var lang: AppLanguage = AppLanguage.PT_BR
        private set
    private var table: Map<String, String> = emptyMap()
    val locale: Locale get() = Locale.forLanguageTag(lang.tag)

    private const val PREFS = "ui"
    private const val KEY = "app_language"

    fun chosen(context: Context): AppLanguage =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { v -> AppLanguage.entries.firstOrNull { it.name == v } } ?: AppLanguage.SYSTEM

    fun save(context: Context, l: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, l.name).commit()
    }

    /** Idioma efetivo: o escolhido ou, por padrão ("Sistema"), o do aparelho. */
    fun resolve(l: AppLanguage): AppLanguage {
        if (l != AppLanguage.SYSTEM) return l
        val sys = Locale.getDefault()
        return when (sys.language) {
            "pt" -> AppLanguage.PT_BR
            "en" -> AppLanguage.EN
            "es" -> AppLanguage.ES
            "fr" -> AppLanguage.FR
            "de" -> AppLanguage.DE
            "it" -> AppLanguage.IT
            "ru" -> AppLanguage.RU
            "ja" -> AppLanguage.JA
            "zh" -> AppLanguage.ZH
            else -> AppLanguage.EN
        }
    }

    fun init(context: Context) {
        lang = resolve(chosen(context))
        table = if (lang == AppLanguage.PT_BR) emptyMap() else load(context, lang.tag)
        Locale.setDefault(locale)
    }

    private fun load(context: Context, tag: String): Map<String, String> = runCatching {
        val json = JSONObject(context.assets.open("i18n/$tag.json").bufferedReader().use { it.readText() })
        buildMap { json.keys().forEach { k -> put(k, json.getString(k)) } }
    }.getOrDefault(emptyMap())

    fun tr(key: String): String = table[key] ?: key
    fun trc(context: String, key: String): String = table["$context|$key"] ?: key
}

/** Como [t], para palavras com dois sentidos em português (ex.: "Início" aba × data de início). */
fun tc(context: String, key: String): String = I18n.trc(context, key)

/** Texto traduzido. Variáveis: {0}, {1}… na ordem dos [args]. */
fun t(key: String, vararg args: Any?): String {
    var s = I18n.tr(key)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}
