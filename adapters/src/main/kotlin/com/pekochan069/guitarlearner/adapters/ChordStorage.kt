package com.pekochan069.guitarlearner.adapters

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.ChordDraft
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordQuality
import com.pekochan069.guitarlearner.domain.ChordShape
import com.pekochan069.guitarlearner.domain.ChordTheory
import com.pekochan069.guitarlearner.domain.GuitarContext
import com.pekochan069.guitarlearner.domain.GuitarPitch
import com.pekochan069.guitarlearner.domain.GuitarTuning
import com.pekochan069.guitarlearner.domain.PitchClass
import com.pekochan069.guitarlearner.domain.SavedChord
import com.pekochan069.guitarlearner.domain.StringStop
import java.util.Collections
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class ChordDocument(val draft: ChordDraft = ChordDraft(), val records: List<SavedChord> = emptyList())

internal class ChordStorage(
    private val preferences: SharedPreferences,
    private val decode: (String) -> ChordDocument = ::decodeChords,
    private val encode: (ChordDocument) -> String = ::encodeChords,
) {
    private var acceptedSource: String? = null
    private var readable = false
    val initial: Either<ChordFailure, ChordDocument> = read()

    fun read(): Either<ChordFailure, ChordDocument> = try {
        val source = preferences.getString(KEY, null)
        val document = source?.let(decode) ?: ChordDocument()
        acceptedSource = source
        readable = true
        document.right()
    } catch (_: JSONException) {
        unreadable()
    } catch (_: IllegalArgumentException) {
        unreadable()
    } catch (_: ClassCastException) {
        unreadable()
    } catch (_: SecurityException) {
        unreadable()
    }

    private fun unreadable(): Either<ChordFailure, ChordDocument> {
        readable = false
        return ChordFailure.ReadFailed.left()
    }

    fun save(document: ChordDocument): Either<ChordFailure, Unit> {
        if (!readable) return ChordFailure.ReadFailed.left()
        val source = encode(document)
        return try {
            if (commitSource(source)) {
                acceptedSource = source
                Unit.right()
            } else {
                restoreAcceptedMemory()
                ChordFailure.WriteFailed.left()
            }
        } catch (_: SecurityException) {
            restoreAcceptedMemory()
            ChordFailure.WriteFailed.left()
        }
    }

    private fun restoreAcceptedMemory() {
        try {
            if (!commitSource(acceptedSource)) Log.w("ChordStorage", "Disk rollback failed; accepted preference memory restored")
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("UseKtx")
    private fun commitSource(source: String?): Boolean {
        val editor = preferences.edit()
        if (source == null) editor.remove(KEY) else editor.putString(KEY, source)
        return editor.commit()
    }

    companion object { const val KEY = "document" }
}

internal fun encodeChords(document: ChordDocument): String = JSONObject()
    .put("version", 1)
    .put("draft", encodeDraft(document.draft))
    .put("records", JSONArray().apply {
        document.records.forEach { put(JSONObject().put("id", it.id).put("content", encodeDraft(it.content))) }
    }).toString()

internal fun decodeChords(source: String): ChordDocument {
    val json = JSONObject(source)
    require(chordInteger(json, "version") == 1)
    val entries = json.getJSONArray("records")
    val ids = mutableSetOf<String>()
    val records = List(entries.length()) { index ->
        val entry = entries.getJSONObject(index)
        val id = chordString(entry, "id")
        require(ids.add(id))
        SavedChord(id, decodeDraft(entry.getJSONObject("content")))
    }
    val draft = decodeDraft(json.getJSONObject("draft"))
    require(draft.targetId == null || draft.targetId in ids)
    return ChordDocument(draft, Collections.unmodifiableList(records))
}

private fun encodeDraft(draft: ChordDraft): JSONObject = JSONObject()
    .put("name", draft.name)
    .put("capo", draft.context.capo)
    .put("tuning", JSONArray().apply {
        draft.context.tuning.pitches.forEach { put(JSONObject().put("note", it.note.ordinal).put("octave", it.octave)) }
    })
    .put("stops", JSONArray().apply { draft.shape.stops.forEach { stop -> put(when (stop) {
        StringStop.Muted -> -1
        StringStop.Open -> 0
        is StringStop.Fretted -> stop.fret
    }) } })
    .put("selected", draft.selected?.let { JSONObject().put("root", it.root.ordinal).put("quality", it.quality.name) } ?: JSONObject.NULL)
    .put("target", draft.targetId ?: JSONObject.NULL)

private fun decodeDraft(json: JSONObject): ChordDraft {
    val tuning = json.getJSONArray("tuning")
    val stops = json.getJSONArray("stops")
    require(tuning.length() == 6 && stops.length() == 6)
    val context = GuitarContext(GuitarTuning(List(6) { index ->
        val pitch = tuning.getJSONObject(index)
        GuitarPitch(chordPitch(pitch, "note"), chordInteger(pitch, "octave"))
    }), chordInteger(json, "capo"))
    val shape = ChordShape(List(6) { index ->
        val value = stops.get(index)
        require(value is Int)
        when (value) { -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(value) }
    })
    val selected = json.get("selected").takeUnless { it === JSONObject.NULL }?.let {
        val identity = json.getJSONObject("selected")
        ChordIdentity(chordPitch(identity, "root"), ChordQuality.valueOf(chordString(identity, "quality")))
    }
    val target = json.get("target").takeUnless { it === JSONObject.NULL }?.let {
        chordString(json, "target").also { id -> require(id.isNotBlank() && id.length <= 80) }
    }
    val draft = ChordDraft(chordString(json, "name"), context, shape, selected, target)
    require(ChordTheory.normalize(draft).selected == selected)
    return draft
}

private fun chordPitch(json: JSONObject, key: String): PitchClass {
    val index = chordInteger(json, key)
    require(index in 0..11)
    return PitchClass.entries[index]
}

private fun chordInteger(json: JSONObject, key: String): Int {
    val value = json.get(key)
    require(value is Int)
    return value
}

private fun chordString(json: JSONObject, key: String): String {
    val value = json.get(key)
    require(value is String)
    return value
}
