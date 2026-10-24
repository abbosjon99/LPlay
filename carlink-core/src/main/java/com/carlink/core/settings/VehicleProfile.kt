package com.carlink.core.settings

import android.content.Context
import android.os.Build

/**
 * Which car-specific integrations the engines may use (design/REBUILD_V2.md §3). Both engines
 * were written for BYD DiLink head units; everything BYD-specific stays off unless the profile is
 * [BYD_DILINK] (docs/dissection/DIPLAY.md §12).
 *
 * LPlay: this build is for the Leapmotor C01 (2024-26). [LEAPMOTOR_C01] is the profile it detects; [BYD_DILINK] is
 * kept so upstream code and tests still compile, but LPlay never detects it and hides the BYD brand.
 */
enum class VehicleProfile {
    /** Any car, tablet or phone. The default. */
    GENERIC,

    /** A BYD DiLink head unit: HUD/cluster outputs, BYD wheel keys and adb helpers allowed. */
    BYD_DILINK,

    /**
     * LPlay: a Leapmotor C01 head unit. None of the BYD integrations (like [GENERIC]); the dashboard map goes to the
     * Leapmotor cluster window, the wheel buttons are left to the car's own music app, and the media session is
     * transport-only.
     */
    LEAPMOTOR_C01,
    ;

    /**
     * LPlay: the car owns the steering-wheel, dock and voice buttons, so the engines keep a transport-only media session and
     * the now-playing notification, and CarLink's learned wheel buttons do nothing.
     */
    val transportOnlyMedia: Boolean get() = this == LEAPMOTOR_C01

    companion object {
        /** The car's own multimedia app; its presence is how a Leapmotor head unit is told from a phone or tablet. */
        const val LEAPMOTOR_MULTIMEDIA_PACKAGE = "com.leapmotor.multimedia"

        /**
         * Leapmotor when the car's multimedia package is installed (the app manifest lists it in `<queries>`) or the
         * build fingerprint names Leapmotor; otherwise [GENERIC]. LPlay does not look for BYD.
         */
        fun detect(context: Context): VehicleProfile = detect(
            multimediaInstalled = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(LEAPMOTOR_MULTIMEDIA_PACKAGE, 0)
            }.isSuccess,
            fingerprint = Build.FINGERPRINT.orEmpty(),
        )

        /** [detect] without Android: the two facts it reads. */
        fun detect(multimediaInstalled: Boolean, fingerprint: String): VehicleProfile =
            if (multimediaInstalled || fingerprint.contains("leapmotor", ignoreCase = true)) LEAPMOTOR_C01 else GENERIC
    }
}
