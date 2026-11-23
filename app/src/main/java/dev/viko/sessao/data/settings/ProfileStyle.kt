package dev.viko.sessao.data.settings

import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.i18n.t
import kotlinx.serialization.Serializable

/** De onde vem o banner do meu perfil. */
@Serializable
enum class BannerSource(val label: String) { NONE(t("Nenhum")), TITLE(t("De um título")), GALLERY(t("Da galeria")) }

/** Fundos animados do cabeçalho do perfil. */
@Serializable
enum class AnimatedBg(val label: String) {
    NONE(t("Nenhum")), AURORA(t("Aurora")), PETALS(t("Pétalas")), STARS(t("Estrelas")), WAVES(t("Ondas")), BUBBLES(t("Bolhas")), EMBERS(t("Brasas"))
}

/** Uma cor de perfil: sólida ou degradê. */
@Serializable
data class ProfileColor(val id: String, val label: String, val colors: List<Long>) {
    companion object {
        val all = listOf(
            ProfileColor("ambar", t("Âmbar"), listOf(0xFFE2B252)),
            ProfileColor("blue", t("Azul"), listOf(0xFF3DB4F2)),
            ProfileColor("purple", t("Roxo"), listOf(0xFFC063FF)),
            ProfileColor("pink", t("Rosa"), listOf(0xFFFC9DD6)),
            ProfileColor("orange", t("Laranja"), listOf(0xFFEF881A)),
            ProfileColor("red", t("Vermelho"), listOf(0xFFE13333)),
            ProfileColor("green", t("Verde"), listOf(0xFF4CCA51)),
            ProfileColor("gray", t("Cinza"), listOf(0xFF677B94)),
            ProfileColor("sunset", t("Pôr do sol"), listOf(0xFFFF7E5F, 0xFFFEB47B)),
            ProfileColor("aurora", t("Aurora"), listOf(0xFF00C9FF, 0xFF92FE9D)),
            ProfileColor("ocean", t("Oceano"), listOf(0xFF2E5BFF, 0xFF1BFFE4)),
            ProfileColor("fire", t("Fogo"), listOf(0xFFF12711, 0xFFF5AF19)),
            ProfileColor("galaxy", t("Galáxia"), listOf(0xFF7F00FF, 0xFFE100FF)),
            ProfileColor("gold", t("Ouro"), listOf(0xFFB8860B, 0xFFFFE08A)),
            ProfileColor("mint", t("Menta"), listOf(0xFF11998E, 0xFF38EF7D)),
            ProfileColor("cinema", t("Cinema"), listOf(0xFFB23A3A, 0xFFE2B252)),
        )

        fun byId(id: String?): ProfileColor? = all.firstOrNull { it.id == id }
    }
}

/** Personalização do meu perfil. Fica só no aparelho (e no backup). */
@Serializable
data class ProfileStyle(
    /** Nome mostrado no perfil; vazio = o nome do Trakt (ou "Você"). */
    val name: String = "",
    val bio: String = "",
    val colorId: String? = null,
    val bannerSource: BannerSource = BannerSource.NONE,
    /** Fundo (backdrop) de um título escolhido como banner. */
    val bannerTitle: TitleKey? = null,
    val bannerPath: String? = null,
    val animatedBg: AnimatedBg = AnimatedBg.NONE,
    /** Foto escolhida da galeria; senão, a do Trakt. */
    val customAvatar: Boolean = false,
    /** Até 6 títulos em destaque no perfil, na ordem escolhida. */
    val favorites: List<TitleKey> = emptyList(),
) {
    companion object { const val MAX_FAVORITES = 6 }
}
