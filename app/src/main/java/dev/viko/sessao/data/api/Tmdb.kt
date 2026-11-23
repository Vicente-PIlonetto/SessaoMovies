package dev.viko.sessao.data.api

import dev.viko.sessao.i18n.t
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/** API v3 do TMDB. Idioma e região vão em toda chamada (pt-BR / BR). */
interface TmdbService {
    @GET("search/multi")
    suspend fun searchMulti(@Query("query") query: String, @Query("page") page: Int = 1, @Query("include_adult") adult: Boolean = false): PageDto<SummaryDto>

    @GET("search/movie")
    suspend fun searchMovie(@Query("query") query: String, @Query("year") year: Int? = null): PageDto<SummaryDto>

    @GET("movie/{id}")
    suspend fun movie(@Path("id") id: Int, @Query("append_to_response") append: String = "credits,similar,watch/providers"): MovieDetailDto

    @GET("tv/{id}")
    suspend fun tv(@Path("id") id: Int, @Query("append_to_response") append: String = "aggregate_credits,similar,watch/providers"): TvDetailDto

    /** Detalhe da série sem extras (datas de episódio para a Agenda). */
    @GET("tv/{id}")
    suspend fun tvBasic(@Path("id") id: Int): TvDetailDto

    /** Só a sinopse em inglês, quando não há em português. */
    @GET("movie/{id}")
    suspend fun movieIn(@Path("id") id: Int, @Query("language") language: String): MovieDetailDto

    @GET("tv/{id}")
    suspend fun tvIn(@Path("id") id: Int, @Query("language") language: String): TvDetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int): SeasonDto

    @GET("{type}/{id}/watch/providers")
    suspend fun providers(@Path("type") type: String, @Path("id") id: Int): WatchProvidersDto

    @GET("trending/all/week")
    suspend fun trending(@Query("page") page: Int = 1): PageDto<SummaryDto>

    @GET("{type}/popular")
    suspend fun popular(@Path("type") type: String, @Query("page") page: Int = 1): PageDto<SummaryDto>

    @GET("movie/now_playing")
    suspend fun nowPlaying(@Query("page") page: Int = 1): PageDto<SummaryDto>

    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @Query("page") page: Int = 1, @retrofit2.http.QueryMap filters: Map<String, String>): PageDto<SummaryDto>

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String): GenreListDto

    @GET("watch/providers/{type}")
    suspend fun providerList(@Path("type") type: String): ProviderListDto
}

/** Erros do TMDB já com a mensagem que a tela mostra. */
sealed class TmdbError(message: String) : Exception(message) {
    class NoKey : TmdbError(t("Sem chave do TMDB. Coloque uma em Configurações > Chave do TMDB."))
    class BadKey : TmdbError(t("O TMDB recusou a chave. Confira em Configurações > Chave do TMDB."))
    class Offline : TmdbError(t("Sem conexão. Isso precisa de internet."))
    class NotFound : TmdbError(t("Título não encontrado no TMDB."))
    class Other(code: Int) : TmdbError(t("O TMDB respondeu com erro ({0}). Tente de novo mais tarde.", code))
}

/** Converte exceções de rede/HTTP para [TmdbError]. */
fun Throwable.toTmdbError(): Throwable = when (this) {
    is TmdbError -> this
    is HttpException -> when (code()) {
        401 -> TmdbError.BadKey()
        404 -> TmdbError.NotFound()
        else -> TmdbError.Other(code())
    }
    is IOException -> TmdbError.Offline()
    else -> this
}

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?, size: String = "w342") = path?.let { BASE + size + it }
    fun backdrop(path: String?) = path?.let { BASE + "w780" + it }
    fun logo(path: String?) = path?.let { BASE + "w92" + it }
    fun profile(path: String?) = path?.let { BASE + "w185" + it }
    fun still(path: String?) = path?.let { BASE + "w300" + it }
}

/**
 * Cliente do TMDB. A chave vem de [key] a cada chamada (a das Configurações, se houver; senão a embutida).
 * Aceita a chave v3 (vai como api_key) ou o token de leitura v4 (vai no cabeçalho).
 * Limita o ritmo das chamadas e espera quando o TMDB responde 429.
 */
class Tmdb(private val key: () -> String) {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val interceptor = Interceptor { chain ->
        val k = key().trim()
        val original = chain.request()
        val url = original.url.newBuilder()
            .apply { if (!k.startsWith("eyJ")) addQueryParameter("api_key", k) }
            .apply { if (original.url.queryParameter("language") == null) addQueryParameter("language", "pt-BR") }
            .addQueryParameter("region", "BR")
            .addQueryParameter("watch_region", "BR")
            .build()
        val req = original.newBuilder().url(url)
            .apply { if (k.startsWith("eyJ")) header("Authorization", "Bearer $k") }
            .header("Accept", "application/json")
            .build()
        proceedWithRateLimit(chain, req)
    }

    private fun proceedWithRateLimit(chain: Interceptor.Chain, req: okhttp3.Request): Response {
        var attempt = 0
        while (true) {
            pace()
            val res = chain.proceed(req)
            if (res.code != 429 || attempt >= 3) return res
            val wait = res.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: (2L shl attempt)
            res.close()
            Thread.sleep(wait * 1000)
            attempt++
        }
    }

    private val lock = Any()
    private var last = 0L

    /** No máximo ~25 chamadas por segundo, bem abaixo do limite do TMDB. */
    private fun pace() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = last + 40 - now
            if (wait > 0) Thread.sleep(wait)
            last = System.currentTimeMillis()
        }
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val service: TmdbService = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(http.newBuilder().addInterceptor(interceptor).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbService::class.java)

    /** Roda uma chamada convertendo os erros para mensagens da tela. */
    suspend fun <T> call(block: suspend TmdbService.() -> T): T {
        // A chave é conferida aqui: uma exceção dentro do interceptor derrubaria a thread do OkHttp.
        if (key().isBlank()) throw TmdbError.NoKey()
        return try { service.block() } catch (e: Throwable) { throw e.toTmdbError() }
    }

    val hasKey: Boolean get() = key().isNotBlank()
}
