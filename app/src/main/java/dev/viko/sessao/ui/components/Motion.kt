package dev.viko.sessao.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/** Molas usadas nas animações do app. */
object Springs {
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    val smooth = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val offset = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

fun coverKey(prefix: String, id: Any) = "c-$prefix-$id"

@OptIn(ExperimentalSharedTransitionApi::class)
private val coverBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.85f, stiffness = 380f) }

/** A capa "voa" entre telas quando a mesma chave aparece na origem e no destino. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedCover.sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = coverBounds,
        )
    }
}

/** Clique com leve "afundar" ao pressionar, e toque longo opcional. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, scaleTo: Float = 0.95f): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, Springs.bouncy, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
}
