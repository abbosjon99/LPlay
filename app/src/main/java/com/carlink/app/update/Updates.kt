package com.carlink.app.update

import android.content.Context
import android.util.Log
import com.carlink.app.BuildConfig
import com.carlink.app.ui.UiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import java.io.File

/** The app's one [UpdateController], wired to Android (network, PackageManager, PackageInstaller). */
object Updates {
    /** The release package; the debug build (`.hudtest`) never checks or installs. */
    const val RELEASE_PACKAGE = "com.carlink.app"
    private const val TAG = "CarLink-Update"

    @Volatile private var instance: UpdateController? = null

    fun get(context: Context): UpdateController = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    /**
     * Only the official release updates itself: a release build of [RELEASE_PACKAGE] signed with
     * the release key. A build signed with another key could not install the update anyway.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isSupported(context: Context): Boolean = false // LPlay: no self-update; LPlay is installed by hand, never from CarLink's releases

    private fun create(app: Context): UpdateController {
        val current = AppVersion.parseVersionName(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)
        val http = UrlConnectionHttp("CarLink/${BuildConfig.VERSION_NAME} (Android)")
        val supported = isSupported(app)
        Log.i(TAG, "updater: version $current, ${if (supported) "updates on" else "updates off (not the signed release build)"}")
        return UpdateController(
            scope = MainScope(),
            io = Dispatchers.IO,
            source = GitHubReleaseSource(http, current),
            http = http,
            verifier = UpdateVerifier(JavaSha256, AndroidApkInspector(app), app.packageName, BuildConfig.VERSION_CODE.toLong()),
            installer = PackageInstallerUpdater(app),
            prefs = UiPreferences(app),
            current = current,
            supported = supported,
            downloadDir = File(app.cacheDir, "updates"),
            log = { Log.i(TAG, it) },
        )
    }
}
