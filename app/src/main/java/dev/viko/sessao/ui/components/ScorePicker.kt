package dev.viko.sessao.ui.components

import dev.viko.sessao.i18n.t
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.viko.sessao.domain.score.Bands
import dev.viko.sessao.domain.score.Letter
import dev.viko.sessao.domain.score.LetterGrade
import dev.viko.sessao.domain.score.LetterModifier
import dev.viko.sessao.domain.score.ScoreSystem
import dev.viko.sessao.domain.score.Scores
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono

/** Valor escolhido, de 0 a 100. */
data class PickedScore(val raw: Int)

/**
 * Folha para dar nota. O sistema pode ser trocado só para esta avaliação.
 */
@Composable
fun ScorePickerSheet(
    initialRaw: Int,
    system: ScoreSystem,
    onDismiss: () -> Unit,
    onSave: (PickedScore) -> Unit,
    /** Título e subtítulo opcionais (ex.: ao completar uma obra). */
    title: String = t("Nota"),
    subtitle: String? = null,
    /** Se definido, troca "Remover nota" por um botão de pular. */
    skipLabel: String? = null,
) {
    var sys by remember { mutableStateOf(system) }
    var picked by remember { mutableStateOf(PickedScore(initialRaw)) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val p = AppTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = p.background,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2)
                SystemSelector(sys) { sys = it }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                ScoreGlyph(picked.raw, sys, 30, 40.dp, p.text)
            }
            Spacer(Modifier.height(16.dp))
            when (sys) {
                ScoreSystem.LETTERS -> LetterPicker(picked) { picked = it }
                ScoreSystem.POINT_10 -> NumberRow((1..10).toList(), if (picked.raw > 0) Scores.point10(picked.raw) else null) { picked = PickedScore(it * 10) }
                ScoreSystem.SMILEY_7 -> FacesRow(
                    Bands.all.reversed().map { Triple(it.value, it.face, it.mid) },
                    Bands.of(picked.raw)?.value,
                ) { picked = PickedScore(it) }
                ScoreSystem.STARS_5 -> StarPicker(if (picked.raw > 0) Scores.starHalves(picked.raw) else 0) { picked = PickedScore(it * 10) }
                ScoreSystem.POINT_100, ScoreSystem.POINT_10_DECIMAL -> SliderPicker(picked.raw, step = 1) { picked = PickedScore(it) }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (skipLabel != null) TextButton(onClick = onDismiss) { Text(skipLabel, color = p.textSecondary) }
                else TextButton(onClick = { onSave(PickedScore(0)) }) { Text(t("Remover nota"), color = p.textSecondary) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(picked) },
                    enabled = skipLabel == null || picked.raw > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                ) { Text(t("Salvar")) }
            }
        }
    }
}

@Composable
private fun SystemSelector(sys: ScoreSystem, onChange: (ScoreSystem) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sys.label, style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.textSecondary)
            Icon(Icons.Default.ArrowDropDown, null, tint = AppTheme.palette.textSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ScoreSystem.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { onChange(it); open = false })
            }
        }
    }
}

@Composable
private fun Cell(text: String, selected: Boolean, special: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(0.75.dp, if (selected) (if (special) p.accent else p.text) else p.divider, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Mono.style(15, if (special) FontWeight.SemiBold else FontWeight.Medium),
            color = when {
                special -> p.accent
                selected -> p.text
                else -> p.textSecondary
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** Fileira F E D C B A e, embaixo, os modificadores. Nota em dois toques. */
@Composable
private fun LetterPicker(picked: PickedScore, onPick: (PickedScore) -> Unit) {
    var row by remember { mutableStateOf(if (picked.raw > 0) LetterGrade.nearest(picked.raw).letter else null) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Letter.entries.forEach { l -> Cell(l.name, selected = row == l, modifier = Modifier.weight(1f)) { row = l } }
    }
    Spacer(Modifier.height(10.dp))
    val letter = row ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        LetterModifier.entries.forEach { m ->
            val raw = letter.value(m)
            Cell(if (m == LetterModifier.NORMAL) letter.name else letter.name + m.suffix, picked.raw == raw, modifier = Modifier.weight(1f)) {
                onPick(PickedScore(raw))
            }
        }
    }
}

@Composable
private fun NumberRow(values: List<Int>, selected: Int?, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = values.size.coerceAtMost(10)) {
        values.forEach { v -> Cell("$v", selected == v, modifier = Modifier.weight(1f)) { onPick(v) } }
    }
}

@Composable
private fun FacesRow(items: List<Triple<Int, String, Int>>, selectedLevel: Int?, onPick: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        items.forEach { (level, label, raw) ->
            val sel = level == selectedLevel
            Column(
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onPick(raw) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Cada carinha na sua cor; as não escolhidas ficam apagadas.
                val col = faceColor(level)
                FaceIcon(level, if (sel || selectedLevel == null) col else col.copy(alpha = 0.4f), if (items.size > 3) 36.dp else 48.dp, label = label)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) p.text else p.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.width(if (items.size > 3) 44.dp else 72.dp))
            }
        }
    }
}

@Composable
private fun StarPicker(halves: Int, onPick: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        for (i in 1..5) {
            val icon = when {
                halves >= i * 2 -> Icons.Filled.Star
                halves == i * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarOutline
            }
            Icon(
                icon, null, tint = if (halves > 0) p.text else p.textTertiary,
                modifier = Modifier.size(48.dp).pointerInput(i) {
                    detectTapGestures { pos -> onPick(if (pos.x < size.width / 2) i * 2 - 1 else i * 2) }
                },
            )
        }
    }
    Text(t("Toque na metade esquerda para meia estrela."), style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
}

@Composable
private fun SliderPicker(raw: Int, step: Int, onPick: (Int) -> Unit) {
    val p = AppTheme.palette
    var v by remember { mutableIntStateOf(raw) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { v = (v - step).coerceAtLeast(1); onPick(v) }) { Text("−", style = Mono.style(20)) }
        Slider(
            value = v.toFloat(),
            onValueChange = { v = it.toInt(); onPick(v) },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = p.text, activeTrackColor = p.text, inactiveTrackColor = p.divider, activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        TextButton(onClick = { v = (v + step).coerceAtMost(100); onPick(v) }) { Text("+", style = Mono.style(20)) }
    }
}
