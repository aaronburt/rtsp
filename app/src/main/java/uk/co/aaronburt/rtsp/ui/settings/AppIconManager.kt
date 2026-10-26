package uk.co.aaronburt.rtsp.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import uk.co.aaronburt.rtsp.R

enum class AppIcon(
    internal val alias: String,
    @get:StringRes val label: Int,
    @get:DrawableRes val image: Int,
) {
    Viewfinder("LauncherViewfinder", R.string.app_icon_viewfinder, R.mipmap.ic_launcher),
    TallyLens("LauncherTallyLens", R.string.app_icon_tally_lens, R.mipmap.ic_launcher_tally_lens),
    StreamLens("LauncherStreamLens", R.string.app_icon_stream_lens, R.mipmap.ic_launcher_stream_lens),
    QuadView("LauncherQuadView", R.string.app_icon_quad_view, R.mipmap.ic_launcher_quad_view),
}

/** PackageManager persists this selection across restarts and app updates. */
class AppIconManager(context: Context) {
    private val packageName = context.packageName
    private val packageManager = context.packageManager

    private fun component(icon: AppIcon) = ComponentName(packageName, "$packageName.${icon.alias}")

    val current: AppIcon
        get() = AppIcon.entries.firstOrNull { icon ->
            when (packageManager.getComponentEnabledSetting(component(icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.Viewfinder
                else -> false
            }
        } ?: AppIcon.Viewfinder

    fun select(icon: AppIcon) {
        fun state(option: AppIcon) = if (option == icon) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.setComponentEnabledSettings(AppIcon.entries.map { option ->
                PackageManager.ComponentEnabledSetting(component(option), state(option), PackageManager.DONT_KILL_APP)
            })
        } else {
            val previous = AppIcon.entries.associateWith {
                packageManager.getComponentEnabledSetting(component(it))
            }
            try {
                // Enable the replacement before removing the old entry on Android 8–12.
                packageManager.setComponentEnabledSetting(component(icon), state(icon), PackageManager.DONT_KILL_APP)
                AppIcon.entries.filter { it != icon }.forEach { option ->
                    packageManager.setComponentEnabledSetting(component(option), state(option), PackageManager.DONT_KILL_APP)
                }
            } catch (failure: Exception) {
                previous.forEach { (option, enabled) ->
                    runCatching {
                        packageManager.setComponentEnabledSetting(component(option), enabled, PackageManager.DONT_KILL_APP)
                    }
                }
                throw failure
            }
        }
    }
}
