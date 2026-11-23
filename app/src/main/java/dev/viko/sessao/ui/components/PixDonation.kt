package dev.viko.sessao.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.viko.sessao.BuildConfig
import dev.viko.sessao.domain.PixCode
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono

/** Linha "Apoiar o projeto" na tela Sobre. Só aparece com uma chave Pix configurada no build. */
@Composable
fun PixDonateRow() {
    if (BuildConfig.PIX_KEY.isBlank()) return
    var open by remember { mutableStateOf(false) }
    LinkRow(t("Apoiar o projeto (Pix)"), icon = Icons.Outlined.Favorite, iconColor = Color(0xFFE0457B)) { open = true }
    if (open) PixDialog { open = false }
}

/** QR Code do Pix e o código "copia e cola". O valor quem escolhe é quem doa, no app do banco. */
@Composable
private fun PixDialog(onDismiss: () -> Unit) {
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val name = BuildConfig.PIX_NAME.ifBlank { "Sessao" }
    val code = remember { PixCode.build(BuildConfig.PIX_KEY, name, BuildConfig.PIX_CITY.ifBlank { "BRASIL" }) }
    val matrix = remember(code) {
        runCatching {
            QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        }.getOrNull()
    }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Apoiar o projeto")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t("O app é gratuito e sem anúncios. Se quiser ajudar, qualquer valor pelo Pix vale."),
                    style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, textAlign = TextAlign.Center,
                )
                // O QR fica sempre preto no branco, para qualquer câmera ler.
                if (matrix != null) Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                    Canvas(Modifier.size(200.dp)) {
                        val cell = size.width / matrix.width
                        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
                Text(PixCode.clean(name, 25), style = MaterialTheme.typography.titleSmall)
                Text(t("Chave aleatória"), style = Mono.style(11), color = p.textTertiary)
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Pix", code))
                copied = true
            }) { Text(if (copied) t("Código copiado") else t("Copiar código Pix"), color = p.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Fechar"), color = p.textSecondary) } },
        shape = RoundedCornerShape(16.dp),
        containerColor = p.background,
    )
}
