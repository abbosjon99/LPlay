package com.carlink.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.carlink.app.R
import uz.carhelper.lplay.cluster.ClusterConfig
import uz.carhelper.lplay.cluster.ClusterDisplay
import uz.carhelper.lplay.cluster.ClusterEdgeFade
import uz.carhelper.lplay.cluster.ClusterPrefs
import uz.carhelper.lplay.cluster.ClusterRoutes
import uz.carhelper.lplay.cluster.ClusterTestCardActivity

// LPlay: Settings for the Leapmotor instrument cluster (Settings > Car, Leapmotor profile only).

/** The cluster map: on or off, how far the left edge fades, and a test card for the first parked test. */
@Composable
internal fun ClusterSettingsGroup(openSheet: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    // A sheet saves its choice and closes; the preferences listener bumps `version` so this group shows it.
    var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val config = remember(version) { ClusterPrefs.load(context) }
    androidx.compose.runtime.DisposableEffect(context) {
        val handle = ClusterPrefs.observe(context) { version++ }
        onDispose { handle.close() }
    }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    GlassGroup(header = { GroupHeader(stringResource(R.string.lplay_cluster_group)) }) {
        SwitchRow(stringResource(R.string.lplay_cluster_map), config.enabled,
            { on -> ClusterPrefs.save(context, ClusterPrefs.load(context).copy(enabled = on)); version++ },
            description = stringResource(R.string.lplay_cluster_map_detail), icon = R.drawable.carlink_ic_navigation,
            tags = { NextConnectionTag() })
        GroupDivider()
        ChoiceRow(stringResource(R.string.lplay_cluster_fade), fadeLabel(config.fadePercent),
            { openSheet(SheetKey.of(EssentialSheet.CLUSTER_FADE)) },
            description = stringResource(R.string.lplay_cluster_fade_detail), icon = R.drawable.carlink_ic_dark_mode)
        GroupDivider()
        ChoiceRow(stringResource(R.string.lplay_cluster_aa), aaModeLabel(config.aaMode),
            { openSheet(SheetKey.of(EssentialSheet.CLUSTER_AA_MODE)) },
            description = stringResource(R.string.lplay_cluster_aa_detail), icon = R.drawable.carlink_ic_directions_car,
            tags = { NextConnectionTag() })
        GroupDivider()
        ChoiceRow(stringResource(R.string.lplay_cluster_test), testLabel(testResult),
            { testResult = ClusterTestCardActivity.show(context, square = true, chosenId = config.displayId) },
            description = stringResource(R.string.lplay_cluster_test_detail), icon = R.drawable.carlink_ic_search)
    }
}

/** How Android Auto draws the cluster picture: a square with side margins first, or the whole 16:9 frame of which the middle shows. */
@Composable
internal fun ClusterAaModeSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val config = remember { ClusterPrefs.load(context) }
    ChoiceSheet(
        stringResource(R.string.lplay_cluster_aa), stringResource(R.string.lplay_cluster_aa_sheet_body), true,
        listOf(
            Preset(ClusterConfig.AA_MARGINS, aaModeLabel(ClusterConfig.AA_MARGINS), stringResource(R.string.lplay_cluster_aa_margins_detail)),
            Preset(ClusterConfig.AA_CROP, aaModeLabel(ClusterConfig.AA_CROP), stringResource(R.string.lplay_cluster_aa_crop_detail)),
        ),
        config.aaMode,
        onSelect = { mode -> ClusterPrefs.save(context, config.copy(aaMode = mode)); onDismiss() }, onDismiss = onDismiss,
    )
}

@Composable
private fun aaModeLabel(mode: Int): String = stringResource(
    if (mode == ClusterConfig.AA_CROP) R.string.lplay_cluster_aa_crop else R.string.lplay_cluster_aa_margins)

/** The fade choices, in percent of the window width; 0 switches the fade off. */
@Composable
internal fun ClusterFadeSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val config = remember { ClusterPrefs.load(context) }
    ChoiceSheet(
        stringResource(R.string.lplay_cluster_fade), stringResource(R.string.lplay_cluster_fade_sheet_body), false,
        ClusterEdgeFade.presets.map { Preset(it, fadeLabel(it)) },
        config.fadePercent,
        onSelect = { percent -> applyFade(context, config, percent); onDismiss() }, onDismiss = onDismiss,
    )
}

private fun applyFade(context: android.content.Context, config: ClusterConfig, percent: Int) {
    ClusterPrefs.save(context, config.copy(fadePercent = percent))
    // The window that is showing now follows at once; the CarPlay window is in this process.
    ClusterRoutes.carPlay.activity.get()?.applyEdgeFade()
}

@Composable
private fun fadeLabel(percent: Int): String =
    if (percent <= 0) stringResource(R.string.lplay_cluster_fade_off) else stringResource(R.string.lplay_cluster_fade_value, percent)

@Composable
private fun testLabel(result: Boolean?): String = when (result) {
    null -> stringResource(R.string.lplay_cluster_test_show)
    true -> stringResource(R.string.lplay_cluster_test_shown)
    false -> stringResource(R.string.lplay_cluster_test_none)
}

/** One line per display, for the diagnostic log. */
internal fun clusterDisplaysLine(context: android.content.Context): String = ClusterDisplay.describe(context)
