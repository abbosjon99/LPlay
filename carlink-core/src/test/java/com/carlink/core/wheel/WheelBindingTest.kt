package com.carlink.core.wheel

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class WheelBindingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clean() { WheelBindings.save(context, emptyMap()) }

    private fun press(action: String = "com.leapmotor.steerwheelcontrol.phone", code: Int = 1) =
        Intent(action).putExtra("action", code)

    @Test fun aSignatureIsTheActionAndItsSmallIntExtras() {
        val intent = press().putExtra("count", 7).putExtra("name", "x").putExtra("stamp", 1_700_000_000_000L).putExtra("big", 99_999)
        val signature = WheelSignature.of(intent)!!
        assertEquals("com.leapmotor.steerwheelcontrol.phone", signature.action)
        // Strings, longs and large ints (counters, timestamps) are not part of what identifies a button.
        assertEquals(mapOf("action" to 1, "count" to 7), signature.ints)
        assertNull(WheelSignature.of(Intent()))
    }

    @Test fun aSignatureMatchesTheSameButtonOnly() {
        val signature = WheelSignature.of(press(code = 1))!!
        assertTrue(signature.matches(press(code = 1)))
        assertFalse(signature.matches(press(code = 4)))
        assertFalse(signature.matches(press(action = "com.leapmotor.steerwheelcontrol.media", code = 1)))
        assertFalse(signature.matches(Intent("com.leapmotor.steerwheelcontrol.phone")))
    }

    @Test fun aSignatureSurvivesSerialising() {
        val signature = WheelSignature("a.b.c", mapOf("action" to 4, "key" to -2))
        assertEquals(signature, WheelSignature.parse(signature.serialize()))
        assertEquals(WheelSignature("a.b.c", emptyMap()), WheelSignature.parse("a.b.c"))
        assertNull(WheelSignature.parse(""))
        assertEquals("c action=4 key=-2", signature.label())
    }

    @Test fun bindingsAreKeptAcrossProcessesInAFile() {
        assertTrue(WheelBindings.load(context).isEmpty())
        val answer = WheelSignature("com.leapmotor.steerwheelcontrol.phone", mapOf("action" to 1))
        val assistant = WheelSignature("com.leapmotor.aispeech.visible.say.trigger", emptyMap())
        WheelBindings.set(context, WheelCommand.ANSWER, answer)
        WheelBindings.set(context, WheelCommand.ASSISTANT, assistant)
        val loaded = WheelBindings.load(context)
        assertEquals(answer, loaded[WheelCommand.ANSWER])
        assertEquals(assistant, loaded[WheelCommand.ASSISTANT])
        assertNull(loaded[WheelCommand.HANG_UP])
        WheelBindings.set(context, WheelCommand.ANSWER, null)
        assertNull(WheelBindings.load(context)[WheelCommand.ANSWER])
        assertNotNull(WheelBindings.load(context)[WheelCommand.ASSISTANT])
    }

    @Test fun aBroadcastFindsTheCommandsLearnedForIt() {
        val bindings = mapOf(
            WheelCommand.ANSWER to WheelSignature("com.leapmotor.steerwheelcontrol.phone", mapOf("action" to 1)),
            WheelCommand.HANG_UP to WheelSignature("com.leapmotor.steerwheelcontrol.phone", mapOf("action" to 4)),
        )
        assertEquals(listOf(WheelCommand.ANSWER), WheelBindings.commandsFor(bindings, press(code = 1)))
        assertEquals(listOf(WheelCommand.HANG_UP), WheelBindings.commandsFor(bindings, press(code = 4)))
        assertTrue(WheelBindings.commandsFor(bindings, press(code = 7)).isEmpty())
    }

    @Test fun theRecorderListsEveryExtraWithItsType() {
        val line = WheelButtonRecorder.describe(press(code = 4).putExtra("who", "wheel"), "12:00:00.000")
        assertEquals("12:00:00.000 com.leapmotor.steerwheelcontrol.phone action=4<Integer> who=wheel<String>", line)
        assertTrue(WheelButtonRecorder.describe(Intent("x.y"), "t").endsWith("(no extras)"))
    }

    @Test fun theRecorderKeepsOnlyTheLastLines() {
        WheelButtonRecorder.clear()
        repeat(WheelButtonRecorder.MAX_LINES + 25) { WheelButtonRecorder.record("line $it") }
        assertEquals(WheelButtonRecorder.MAX_LINES, WheelButtonRecorder.lineCount())
        assertTrue(WheelButtonRecorder.snapshot().startsWith("line 25"))
        WheelButtonRecorder.clear()
    }
}
