package dev.viko.sessao.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.viko.sessao.data.settings.Accent

/** O ícone do lançador acompanha a cor de destaque: um activity-alias por cor no manifesto. */
object AppIcon {
    private fun alias(accent: Accent) = ".Icon_${accent.name.lowercase()}"

    fun apply(context: Context, accent: Accent) {
        val pm = context.packageManager
        val name = { a: String -> ComponentName(context.packageName, context.packageName + a) }
        val target = alias(accent)
        val all = Accent.entries.map { alias(it) }
        val current = all.firstOrNull { a ->
            when (pm.getComponentEnabledSetting(name(a))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a == alias(Accent.AMBAR)
                else -> false
            }
        }
        if (current == target) return
        pm.setComponentEnabledSetting(name(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it != target }.forEach {
            pm.setComponentEnabledSetting(name(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
