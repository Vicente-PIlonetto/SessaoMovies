package dev.viko.sessao.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.settings.AnimatedBg
import dev.viko.sessao.data.settings.BannerSource
import dev.viko.sessao.data.settings.ProfileColor
import dev.viko.sessao.data.settings.ProfileStyle
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.ColorSwatch
import dev.viko.sessao.ui.components.Cover
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.Pill
import dev.viko.sessao.ui.components.ProfileHeader
import dev.viko.sessao.ui.components.galleryAvatarFile
import dev.viko.sessao.ui.components.galleryBannerFile
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.components.resolveAvatar
import dev.viko.sessao.ui.components.resolveBanner
import dev.viko.sessao.ui.nav.LocalContainer
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Copia a imagem escolhida para dentro do app (o link da galeria pode deixar de valer). */
private suspend fun copyImage(context: Context, uri: Uri, to: File): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> to.outputStream().use { input.copyTo(it) } } != null
    }.getOrDefault(false)
}

@Composable
fun CustomizeProfileScreen(nav: Nav) {
    val c = LocalContainer.current
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by c.settings.profileStyle.collectAsStateWithLifecycle(initialValue = null)
    // Espera o perfil salvo carregar: os campos de texto começam com ele.
    val style = loaded ?: return
    val session by c.trakt.session.collectAsStateWithLifecycle()
    val items by c.lists.items.collectAsStateWithLifecycle(initialValue = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    // Força a prévia a reler as imagens depois de escolher outra.
    var version by remember { mutableStateOf(0) }
    fun save(s: ProfileStyle) = scope.launch { c.settings.setProfileStyle(s) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (copyImage(ctx, uri, galleryAvatarFile(ctx))) { save(style.copy(customAvatar = true)); version++ } else message = t("Não foi possível usar essa imagem.")
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (copyImage(ctx, uri, galleryBannerFile(ctx))) { save(style.copy(bannerSource = BannerSource.GALLERY)); version++ } else message = t("Não foi possível usar essa imagem.")
        }
    }
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        // Prévia ao vivo.
        val name = style.name.trim().ifBlank { session?.name?.takeIf { it.isNotBlank() } ?: session?.username ?: t("Você") }
        androidx.compose.runtime.key(version) {
            ProfileHeader(
                name = name,
                avatar = resolveAvatar(ctx, style, session?.avatar),
                banner = resolveBanner(ctx, style),
                color = ProfileColor.byId(style.colorId),
                animated = style.animatedBg,
                subtitle = session?.username?.let { "@$it" },
                top = {
                    IconButton(onClick = nav::back, colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar"))
                    }
                    Text(t("Personalizar perfil"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                },
            )
        }
        message?.let { MessageBar(it, t("OK")) { message = null } }
        if (style.bio.isNotBlank()) Text(style.bio, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        Group(t("Nome e bio")) {
            var nameText by remember { mutableStateOf(style.name) }
            var bioText by remember { mutableStateOf(style.bio) }
            OutlinedTextField(
                nameText, { nameText = it.take(40); save(style.copy(name = nameText)) }, singleLine = true,
                label = { Text(t("Nome")) }, placeholder = { Text(session?.name ?: session?.username ?: t("Você")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                bioText, { bioText = it.take(160); save(style.copy(bio = bioText)) }, minLines = 2,
                label = { Text(t("Bio")) }, supportingText = { Text("${bioText.length}/160", style = Mono.style(11)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp),
            )
        }

        Group(t("Foto")) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (session != null) Pill(t("Do Trakt"), !style.customAvatar, { save(style.copy(customAvatar = false)) })
                Pill(t("Da galeria"), style.customAvatar, { pickAvatar.launch(image) })
            }
        }

        Group(t("Cor")) {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileColor.all.forEach { col ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp).pressable(onClick = { save(style.copy(colorId = col.id)) })) {
                        ColorSwatch(col, style.colorId == col.id || (style.colorId == null && col.id == "ambar"))
                        Text(col.label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
                    }
                }
            }
        }

        Group(t("Banner")) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(BannerSource.NONE.label, style.bannerSource == BannerSource.NONE, { save(style.copy(bannerSource = BannerSource.NONE)) })
                Pill(BannerSource.TITLE.label, style.bannerSource == BannerSource.TITLE, { save(style.copy(bannerSource = BannerSource.TITLE)) })
                Pill(BannerSource.GALLERY.label, style.bannerSource == BannerSource.GALLERY, { pickBanner.launch(image) })
            }
            if (style.bannerSource == BannerSource.TITLE) {
                val withBackdrop = items.mapNotNull { it.title }.filter { it.backdropPath != null }.sortedBy { it.name }
                if (withBackdrop.isEmpty()) Text(t("Nenhum título da lista tem imagem de fundo ainda."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(16.dp))
                LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(withBackdrop, key = { it.key.toString() }) { tt ->
                        val sel = style.bannerTitle == tt.key
                        Column(Modifier.width(150.dp).pressable(onClick = { save(style.copy(bannerSource = BannerSource.TITLE, bannerTitle = tt.key, bannerPath = tt.backdropPath)) })) {
                            AsyncImage(
                                TmdbImages.backdrop(tt.backdropPath), tt.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(p.background)
                                    .then(if (sel) Modifier.border(2.dp, p.accent, RoundedCornerShape(12.dp)) else Modifier),
                            )
                            Text(tt.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (sel) p.accent else p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else Spacer(Modifier.height(16.dp))
        }

        Group(t("Fundo animado")) {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedBg.entries.forEach { bg -> Pill(bg.label, style.animatedBg == bg, { save(style.copy(animatedBg = bg)) }) }
            }
        }

        Group(t("Favoritos")) {
            Text(
                t("Escolha até {0} títulos da sua lista para mostrar no perfil. Toque de novo para tirar.", ProfileStyle.MAX_FAVORITES),
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            val candidates = items.filter { it.title != null && it.entry.status != dev.viko.sessao.domain.WatchStatus.PLANNING }
                .sortedWith(compareByDescending<dev.viko.sessao.domain.Item> { it.key in style.favorites }.thenByDescending { it.entry.scoreRaw })
            if (candidates.isEmpty()) Text(t("Marque algo como visto para escolher favoritos."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(candidates, key = { it.key.toString() }) { item ->
                    val pos = style.favorites.indexOf(item.key)
                    Box(Modifier.pressable(onClick = {
                        val fav = style.favorites
                        val next = when {
                            pos >= 0 -> fav - item.key
                            fav.size >= ProfileStyle.MAX_FAVORITES -> { message = t("O perfil mostra até {0} favoritos.", ProfileStyle.MAX_FAVORITES); fav }
                            else -> fav + item.key
                        }
                        save(style.copy(favorites = next))
                    })) {
                        Cover(TmdbImages.poster(item.title?.posterPath, "w185"), 76.dp, color = item.title?.posterColor)
                        if (pos >= 0) Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(p.accent),
                            contentAlignment = Alignment.Center,
                        ) { Text("${pos + 1}", style = Mono.style(12, FontWeight.SemiBold), color = Color.Black) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
