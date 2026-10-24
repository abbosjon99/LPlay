package com.carlink.core.connection

import android.app.Activity
import android.content.Context
import android.view.Surface
import com.shilapi.xcertplay.ClusterRouteHook
import uz.carhelper.lplay.cluster.ClusterDisplay
import uz.carhelper.lplay.cluster.ClusterPrefs
import uz.carhelper.lplay.cluster.ClusterRoutes

/**
 * LPlay: connects the shared cluster window ([ClusterRoutes.carPlay]) to the CarPlay engine. The engine makes the calls
 * it makes on its adb route (`ClusterRouteHook`); they land on the window's route. Installed for the Leapmotor profile
 * only ([CarPlayAdapter.install]).
 */
internal object CarPlayClusterHook : ClusterRouteHook {
    private val route get() = ClusterRoutes.carPlay

    /** The driver's choices come from the main process' preferences. */
    fun install() {
        route.config = { context -> ClusterPrefs.load(context) }
    }

    override fun enabled(context: Context) = route.enabled(context)
    override fun bind(owner: Any, callback: (Surface?) -> Unit) = route.bind(owner, callback)
    override fun ensure(host: Activity) = route.ensure(host)
    override fun hasConfirmedRoute() = route.hasConfirmedRoute()
    override fun routeSettling() = route.routeSettling()
    override fun setStreamActive(active: Boolean) = route.setStreamActive(active)
    override fun stop(owner: Any) = route.stop(owner)
    override fun streamSize(context: Context): Pair<Int, Int>? =
        ClusterDisplay.targetSize(context, route.config(context).displayId)?.let { it.x to it.y }
}
