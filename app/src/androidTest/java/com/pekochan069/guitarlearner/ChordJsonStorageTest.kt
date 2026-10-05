package com.pekochan069.guitarlearner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.pekochan069.guitarlearner.adapters.AndroidChordsHost
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordQuality
import com.pekochan069.guitarlearner.domain.PitchClass
import com.pekochan069.guitarlearner.domain.StringStop
import com.pekochan069.guitarlearner.domain.TuningPreset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordJsonStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceName = "chord-json-test-${UUID.randomUUID()}"
    private lateinit var preferences: SharedPreferences

    @Before fun setUp() { preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE) }
    @After fun tearDown() { context.deleteSharedPreferences(preferenceName) }

    @Test fun realAndroidJsonRestoresSelectedInterpretationContextAndIndependentUnfinishedDraft() = runBlocking {
        val host = AndroidChordsHost(preferences)
        val stops = listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Fretted(2), StringStop.Fretted(1), StringStop.Fretted(3))
        for (index in 0..5) succeed(host, ChordCommand.SetStop(index, stops[index]))
        succeed(host, ChordCommand.SetCapo(2))
        succeed(host, ChordCommand.SelectCandidate(ChordIdentity(PitchClass.B, ChordQuality.MinorSeventh)))
        succeed(host, ChordCommand.SetName("나만의 코드 · personal"))
        succeed(host, ChordCommand.SaveDraft)
        val original = host.current.value.records.single()
        succeed(host, ChordCommand.SetTuning(TuningPreset.DropD.tuning))
        succeed(host, ChordCommand.SetStop(1, StringStop.Fretted(5)))
        succeed(host, ChordCommand.SetName("Unfinished draft"))
        val json = JSONObject(preferences.getString("document", null)!!)
        assertEquals(1, json.getInt("version"))
        assertEquals(1, json.getJSONArray("records").length())
        val restarted = AndroidChordsHost(preferences)
        assertEquals(null, restarted.current.value.readFailure)
        assertEquals(host.current.value.draft, restarted.current.value.draft)
        assertEquals(listOf(original), restarted.current.value.records)
        succeed(restarted, ChordCommand.LoadRecord(original.id))
        assertEquals(original.content.copy(targetId = original.id), restarted.current.value.draft)
        assertEquals(ChordIdentity(PitchClass.B, ChordQuality.MinorSeventh), restarted.current.value.draft.selected)
        succeed(restarted, ChordCommand.DeleteRecord(original.id))
        val afterDelete = AndroidChordsHost(preferences)
        assertTrue(afterDelete.current.value.records.isEmpty())
        assertEquals(null, afterDelete.current.value.draft.targetId)
        assertEquals(original.content, afterDelete.current.value.draft)
    }

    @Test fun realAndroidJsonRejectsUnsupportedCorruptAndDanglingDataWithoutOverwritingIt() = runBlocking {
        val host = AndroidChordsHost(preferences)
        succeed(host, ChordCommand.SetStop(0, StringStop.Open))
        succeed(host, ChordCommand.SetName("Note"))
        succeed(host, ChordCommand.SaveDraft)
        val valid = preferences.getString("document", null)!!
        val invalid = listOf(
            "not json",
            JSONObject(valid).put("version", 2).toString(),
            JSONObject(valid).apply { getJSONObject("draft").put("capo", 1.5) }.toString(),
            JSONObject(valid).apply { getJSONObject("draft").put("target", "missing-id") }.toString(),
            JSONObject(valid).apply { getJSONObject("draft").put("stops", JSONArray().put(-1)) }.toString(),
            JSONObject(valid).apply { val records = getJSONArray("records"); records.put(records.getJSONObject(0)) }.toString(),
            JSONObject(valid).apply { getJSONObject("draft").getJSONArray("tuning").getJSONObject(0).put("octave", "2") }.toString(),
        )
        for (source in invalid) {
            replaceDocument(source)
            val rejected = AndroidChordsHost(preferences)
            assertEquals(ChordFailure.ReadFailed, rejected.current.value.readFailure)
            assertEquals(Either.Left(ChordFailure.ReadFailed), rejected.execute(ChordCommand.SetName("Keep my work")))
            assertEquals(source, preferences.getString("document", null))
            assertEquals("Keep my work", rejected.current.value.draft.name)
        }
    }

    @SuppressLint("UseKtx")
    private fun replaceDocument(source: String) { assertTrue(preferences.edit().putString("document", source).commit()) }

    private suspend fun succeed(host: AndroidChordsHost, command: ChordCommand) { assertEquals(Either.Right(Unit), host.execute(command)) }
}
