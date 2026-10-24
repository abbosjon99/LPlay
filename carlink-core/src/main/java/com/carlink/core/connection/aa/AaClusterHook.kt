package com.carlink.core.connection.aa

import android.content.Context
import android.view.Surface
import com.andrerinas.openheadunit.AaClusterRouteHook
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.HostIntegration
import uz.carhelper.lplay.cluster.ClusterConfig
import uz.carhelper.lplay.cluster.ClusterEdgeFade
import uz.carhelper.lplay.cluster.ClusterRoutes
import uz.carhelper.lplay.cluster.ClusterStreamGeometry

/**
 * LPlay: connects the shared cluster window ([ClusterRoutes.androidAuto]) to the Android Auto engine. It runs in the
 * engine's ":aa" process, next to the cluster video decoder, and takes its choices from the engine's own settings, which
 * the main process wrote for this session ([AaSessionSettings]). Installed in every process (the application class);
 * it does nothing unless those settings turn the Leapmotor cluster window on.
 */
object AaClusterHook : AaClusterRouteHook {
    private val route get() = ClusterRoutes.androidAuto

    fun install() {
        route.config = { context ->
            val settings = App.provide(context).settings
            ClusterConfig(
                enabled = settings.clusterMapEnabled && settings.lplayClusterWindow,
                displayId = settings.lplayClusterDisplay,
                fadePercent = ClusterEdgeFade.sanitize(settings.lplayClusterFade),
                aaMode = settings.lplayClusterMode,
            )
        }
        // The phone draws a 1280x720 frame (the only size that holds the square); the window shows its middle 720 columns.
        route.geometry = ClusterStreamGeometry.ANDROID_AUTO_SQUARE
        HostIntegration.clusterRoute = this
    }

    override fun enabled(context: Context) = route.enabled(context)
    override fun bind(owner: Any, callback: (Surface?) -> Unit) = route.bind(owner, callback)
    override fun ensure(host: Context) = route.ensure(host)
    override fun setStreamActive(active: Boolean) = route.setStreamActive(active)
    override fun stop(owner: Any) = route.stop(owner)
}
