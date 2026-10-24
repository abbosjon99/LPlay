package uz.carhelper.lplay.cluster

import android.content.Context

/**
 * What the driver chose for the cluster map. One value per engine process, read when the route needs it.
 *
 * @param enabled whether the map goes to the cluster at all.
 * @param displayId a display the driver picked, or [ClusterDisplay.AUTO] for the cluster's known identity.
 * @param fadePercent how far into the window the left edge fades, see [ClusterEdgeFade.presets].
 */
data class ClusterConfig(
    val enabled: Boolean = false,
    val displayId: Int = ClusterDisplay.AUTO,
    val fadePercent: Int = ClusterEdgeFade.DEFAULT_PERCENT,
) {
    companion object {
        val OFF = ClusterConfig()
    }
}

/** The driver's cluster choices, stored in the main process (preferences `lplay_cluster`). */
object ClusterPrefs {
    private const val FILE = "lplay_cluster"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DISPLAY = "display_id"
    private const val KEY_FADE = "fade_percent"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): ClusterConfig = prefs(context).let {
        ClusterConfig(
            enabled = it.getBoolean(KEY_ENABLED, false),
            displayId = it.getInt(KEY_DISPLAY, ClusterDisplay.AUTO),
            fadePercent = ClusterEdgeFade.sanitize(it.getInt(KEY_FADE, ClusterEdgeFade.DEFAULT_PERCENT)),
        )
    }

    fun save(context: Context, config: ClusterConfig) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_DISPLAY, config.displayId)
            .putInt(KEY_FADE, ClusterEdgeFade.sanitize(config.fadePercent))
            .apply()
    }
}
