package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.view.Surface

/**
 * LPlay: lets the host app put the CarPlay dashboard map in its own cluster window (the Leapmotor C01's instrument
 * cluster), instead of DiPlay's BYD presentations and its adb-started activity. The calls are the ones the host
 * already makes on the adb route ([ClusterActivityOutput]); when [CarPlayHostIntegration.clusterRoute] is set, the
 * projection makes them here instead. Main thread only.
 */
interface ClusterRouteHook {
    /** Whether the driver turned the cluster map on. */
    fun enabled(context: Context): Boolean

    /** The projection owns the route from now on; [callback] gets the window's Surface (null when it goes away). */
    fun bind(owner: Any, callback: (Surface?) -> Unit)

    /** Opens the window unless one is there or being opened. */
    fun ensure(host: Activity)

    /** The window is up on the cluster display. */
    fun hasConfirmedRoute(): Boolean

    /** A launch is running and the window has not confirmed its display yet. */
    fun routeSettling(): Boolean

    /** Whether the cluster stream is on the window now. */
    fun setStreamActive(active: Boolean)

    /** [owner] no longer needs the window. */
    fun stop(owner: Any)

    /** Width and height of the stream the window is sized for, or null when there is no cluster display. */
    fun streamSize(context: Context): Pair<Int, Int>?
}
