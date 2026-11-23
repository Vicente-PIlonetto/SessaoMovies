package dev.viko.sessao.ui.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TmdbError
import dev.viko.sessao.data.letterboxd.LetterboxdError
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.nav.LocalContainer

@Composable
inline fun <reified T : ViewModel> appViewModel(key: String? = null, noinline create: (AppContainer) -> T): T {
    val c = LocalContainer.current
    return viewModel(key = key) { create(c) }
}

val LOAD_ERROR get() = t("Não foi possível carregar. Verifique a conexão e tente de novo.")
val REFRESH_ERROR get() = t("Não foi possível atualizar. Verifique a conexão e puxe para tentar de novo.")

/** Mensagem para a tela: a do TMDB/Letterboxd quando houver, senão [fallback]. */
fun Throwable.toUserMessage(fallback: String = LOAD_ERROR): String = when (this) {
    is TmdbError -> message ?: fallback
    is LetterboxdError -> message ?: fallback
    is dev.viko.sessao.data.trakt.TraktError -> message ?: fallback
    else -> fallback
}
