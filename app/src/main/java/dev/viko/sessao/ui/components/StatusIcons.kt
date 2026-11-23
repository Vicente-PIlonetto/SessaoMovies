package dev.viko.sessao.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.WatchStatus

fun WatchStatus.icon(): ImageVector = when (this) {
    WatchStatus.PLANNING -> Icons.Outlined.BookmarkBorder
    WatchStatus.WATCHING -> Icons.Outlined.PlayCircle
    WatchStatus.CAUGHT_UP -> Icons.Outlined.EventAvailable
    WatchStatus.PAUSED -> Icons.Outlined.PauseCircle
    WatchStatus.COMPLETED -> Icons.Outlined.CheckCircle
    WatchStatus.REWATCHING -> Icons.Outlined.Replay
    WatchStatus.DROPPED -> Icons.Outlined.DoNotDisturbOn
}

@Composable
fun StatusIcon(status: WatchStatus, tint: Color, size: Dp = 16.dp) {
    AnimatedContent(status, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() }, label = "status") { st ->
        Icon(st.icon(), null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Ícone + nome do status. */
@Composable
fun StatusLabel(status: WatchStatus, type: MediaType, color: Color? = null, style: TextStyle = MaterialTheme.typography.labelSmall, iconSize: Dp = 14.dp) {
    val c = color ?: dev.viko.sessao.ui.theme.statusColor(status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(status, c, iconSize)
        Spacer(Modifier.width(4.dp))
        Text(status.label(type), style = style, color = c, maxLines = 1)
    }
}
