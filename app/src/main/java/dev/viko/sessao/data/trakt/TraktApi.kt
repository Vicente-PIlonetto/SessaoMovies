package dev.viko.sessao.data.trakt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.viko.sessao.i18n.t
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TraktService {
    @POST("oauth/device/code") suspend fun deviceCode(@Body body: Map<String, String>): DeviceCode
    @POST("oauth/device/token") suspend fun deviceToken(@Body body: DeviceTokenRequest): retrofit2.Response<TokenResponse>
    @POST("oauth/token") suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @GET("users/me") suspend fun me(@Query("extended") extended: String = "full"): TraktUser

    @GET("sync/history") suspend fun history(
        @Query("start_at") startAt: String?,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 100,
    ): retrofit2.Response<List<HistoryItem>>

    @GET("sync/watched/movies") suspend fun watchedMovies(): List<WatchedMovie>
    @GET("sync/ratings/{type}") suspend fun ratings(@Path("type") type: String): List<RatingItem>
    @GET("sync/watchlist") suspend fun watchlist(): List<WatchlistItem>

    @POST("sync/history") suspend fun addHistory(@Body body: SyncBody): retrofit2.Response<Unit>
    @POST("sync/ratings") suspend fun addRatings(@Body body: SyncBody): retrofit2.Response<Unit>
    @POST("sync/watchlist") suspend fun addWatchlist(@Body body: SyncBody): retrofit2.Response<Unit>

    // Social (etapa 2)
    @GET("users/{user}/following") suspend fun following(@Path("user") user: String = "me"): List<FollowItem>
    @GET("users/{user}/history") suspend fun userHistory(@Path("user") user: String, @Query("limit") limit: Int = 20): List<HistoryItem>
}

/** Erros do Trakt já com a mensagem da tela. */
sealed class TraktError(message: String) : Exception(message) {
    class NoClient : TraktError(t("Este APK não tem o Client ID do Trakt."))
    class LoggedOut : TraktError(t("Entre no Trakt de novo em Configurações > Trakt."))
    class Offline : TraktError(t("Sem conexão. Isso precisa de internet."))
    class AccountLimit : TraktError(t("A sua conta do Trakt chegou no limite do plano gratuito. Parte da lista não foi enviada."))
    class Blocked : TraktError(t("O Trakt recusou o acesso agora. Tente de novo mais tarde."))
    class Other(code: Int) : TraktError(t("O Trakt respondeu com erro ({0}). Tente de novo mais tarde.", code))
}

fun Throwable.toTraktError(): Throwable = when (this) {
    is TraktError -> this
    is HttpException -> when (code()) {
        401 -> TraktError.LoggedOut()
        403 -> TraktError.Blocked()
        420 -> TraktError.AccountLimit()
        else -> TraktError.Other(code())
    }
    is IOException -> TraktError.Offline()
    else -> this
}

/**
 * Cliente do Trakt: cabeçalhos obrigatórios, token do usuário e ritmo das chamadas
 * (leituras a cada 0,7 s, abaixo de 500 a cada 5 min; envios 1 por segundo; espera o Retry-After no 429).
 */
class TraktClient(val clientId: String, private val token: () -> String?) {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = false }

    private val lock = Any()
    private var lastPost = 0L
    private var lastGet = 0L

    private val interceptor = Interceptor { chain ->
        val original = chain.request()
        val b = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("User-Agent", "SessaoMovies/1.0")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)
        val tk = token()
        if (tk != null && !original.url.encodedPath.startsWith("/oauth/")) b.header("Authorization", "Bearer $tk")
        val req = b.build()
        var attempt = 0
        while (true) {
            pace(req.method != "GET")
            val res: Response = chain.proceed(req)
            if (res.code != 429 || attempt >= 3) return@Interceptor res
            val wait = res.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 30) ?: (2L shl attempt)
            res.close()
            Thread.sleep(wait * 1000)
            attempt++
        }
        @Suppress("UNREACHABLE_CODE") error("")
    }

    private fun pace(write: Boolean) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val wait = if (write) lastPost + 1100 - now else lastGet + 700 - now
        if (wait > 0) Thread.sleep(wait)
        if (write) lastPost = System.currentTimeMillis() else lastGet = System.currentTimeMillis()
    }

    val service: TraktService = Retrofit.Builder()
        .baseUrl("https://api.trakt.tv/")
        .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TraktService::class.java)

    suspend fun <T> call(block: suspend TraktService.() -> T): T {
        if (clientId.isBlank()) throw TraktError.NoClient()
        return try { service.block() } catch (e: Throwable) { throw e.toTraktError() }
    }
}

/** Sessão do Trakt guardada no aparelho. */
@Serializable
data class TraktSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val username: String = "",
    val name: String? = null,
    val avatar: String? = null,
)

/** Guarda a sessão do Trakt cifrada com AES-GCM, com a chave no Android Keystore. */
class TraktTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("trakt", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    @Volatile private var cached: TraktSession? = null

    fun save(s: TraktSession) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(json.encodeToString(TraktSession.serializer(), s).toByteArray())
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(enc, Base64.NO_WRAP))
            .apply()
        cached = s
    }

    fun load(): TraktSession? = cached ?: runCatching {
        val iv = prefs.getString("iv", null) ?: return null
        val enc = prefs.getString("data", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        json.decodeFromString(TraktSession.serializer(), String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP))))
    }.getOrNull()?.also { cached = it }

    fun clear() {
        cached = null
        prefs.edit().clear().apply()
    }

    companion object {
        private const val ALIAS = "sessao_trakt"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
