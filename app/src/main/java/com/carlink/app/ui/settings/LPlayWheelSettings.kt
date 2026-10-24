package com.carlink.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.carlink.app.R
import com.carlink.app.diagnostics.CarLinkFileProvider
import com.carlink.core.wheel.WheelBindings
import com.carlink.core.wheel.WheelButtonRecorder
import com.carlink.core.wheel.WheelCommand
import com.carlink.core.wheel.WheelLearner
import java.io.File

// LPlay: the car's own buttons (Settings > Car, Leapmotor profile only).

/**
 * Two things for the parked test of the steering wheel. The recorder writes down what the car's buttons send. "Learn"
 * stores the next button the car sends as the answer-call, hang-up or assistant button, and from then on that button
 * answers or ends the CarPlay or Android Auto call, or starts Siri or the Google Assistant. Both only listen: the car's own
 * apps and LP Music see every button exactly as before.
 */
@Composable
internal fun WheelButtonsGroup() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val bindings = remember(version) { WheelBindings.load(context) }
    var learning by remember { mutableStateOf<WheelCommand?>(null) }
    var recording by remember { mutableStateOf(WheelButtonRecorder.running) }
    GlassGroup(header = { GroupHeader(stringResource(R.string.lplay_wheel_group)) }) {
        SwitchRow(stringResource(R.string.lplay_wheel_recorder), recording,
            { on ->
                if (on) WheelButtonRecorder.start(context) else WheelButtonRecorder.stop()
                recording = WheelButtonRecorder.running
                version++
            },
            description = stringResource(R.string.lplay_wheel_recorder_detail), icon = R.drawable.carlink_ic_search_hands_free)
        GroupDivider()
        val lines = remember(version, recording) { WheelButtonRecorder.lineCount() }
        ChoiceRow(stringResource(R.string.lplay_wheel_share), stringResource(R.string.lplay_wheel_lines, lines),
            { shareRecording(context) },
            description = stringResource(R.string.lplay_wheel_share_detail), icon = R.drawable.carlink_ic_ios_share)
        WheelCommand.entries.forEach { command ->
            GroupDivider()
            val signature = bindings[command]
            ChoiceRow(stringResource(commandTitle(command)),
                when {
                    learning == command -> stringResource(R.string.lplay_wheel_press_now)
                    signature != null -> signature.label()
                    else -> stringResource(R.string.carlink_settings_not_set)
                },
                {
                    if (learning == command) {
                        WheelLearner.cancel()
                        learning = null
                    } else {
                        learning = command
                        WheelLearner.start(context, command) { learning = null; version++ }
                    }
                },
                description = stringResource(R.string.lplay_wheel_learn_detail), icon = R.drawable.carlink_ic_search)
        }
        if (bindings.isNotEmpty()) {
            GroupDivider()
            ChoiceRow(stringResource(R.string.lplay_wheel_clear), "",
                { WheelBindings.save(context, emptyMap()); version++ },
                description = stringResource(R.string.lplay_wheel_clear_detail), icon = R.drawable.carlink_ic_close)
        }
    }
}

private fun commandTitle(command: WheelCommand): Int = when (command) {
    WheelCommand.ANSWER -> R.string.lplay_wheel_answer
    WheelCommand.HANG_UP -> R.string.lplay_wheel_hangup
    WheelCommand.ASSISTANT -> R.string.lplay_wheel_assistant
}

private fun shareRecording(context: android.content.Context) {
    val text = WheelButtonRecorder.snapshot().ifEmpty { "(nothing recorded)" }
    val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("wheel-recording.txt")
    runCatching { file.writeText(text) }.onSuccess {
        CarLinkFileProvider.shareText(context, file, "LPlay wheel button recording", "Share the recording")
    }
}
