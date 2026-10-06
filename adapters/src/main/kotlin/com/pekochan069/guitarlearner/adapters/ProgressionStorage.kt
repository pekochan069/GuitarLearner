package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class ProgressionDocument(val draft: ProgressionDraft = ProgressionDraft(), val records: List<SavedProgression> = emptyList())

internal class ProgressionStorage(private val preferences: SharedPreferences,
    private val decode: (String) -> ProgressionDocument = ::decodeProgressions,
    private val encode: (ProgressionDocument) -> String = ::encodeProgressions) {
    private var accepted: String? = null
    private var readable = false
    val initial = read()
    fun read(): Either<ProgressionFailure, ProgressionDocument> = try {
        val source = preferences.getString("document", null)
        val result = source?.let(decode) ?: ProgressionDocument()
        accepted = source; readable = true
        result.right()
    } catch (_: JSONException) { unreadable() }
      catch (_: IllegalArgumentException) { unreadable() }
      catch (_: ClassCastException) { unreadable() }
      catch (_: SecurityException) { unreadable() }
    private fun unreadable(): Either<ProgressionFailure, ProgressionDocument> {
        readable = false
        return ProgressionFailure.ReadFailed.left()
    }
    fun save(document: ProgressionDocument): Either<ProgressionFailure, Unit> {
        if (!readable) return ProgressionFailure.ReadFailed.left()
        val source = encode(document)
        val success = try { commit(source) } catch (_: SecurityException) { false }
        if (success) { accepted = source; return Unit.right() }
        try { commit(accepted) } catch (_: SecurityException) { }
        return ProgressionFailure.WriteFailed.left()
    }
    private fun commit(source: String?): Boolean = preferences.commitString("document", source)
}

internal fun encodeProgressions(document: ProgressionDocument): String = JSONObject().put("version", 1)
    .put("draft", JSONObject().put("name", document.draft.name).put("target", document.draft.targetId ?: JSONObject.NULL)
        .put("content", encodeProgression(document.draft.content)))
    .put("records", JSONArray().apply { document.records.forEach { record ->
        put(JSONObject().put("id", record.id).put("name", record.name).put("content", encodeProgression(record.content)))
    } }).toString()

internal fun decodeProgressions(source: String): ProgressionDocument {
    val json = JSONObject(source)
    require(json.int("version") == 1)
    val ids = mutableSetOf<String>()
    val records = json.getJSONArray("records").objects().map {
        SavedProgression(it.text("id"), it.text("name"), decodeProgression(it.getJSONObject("content")))
            .also { record -> require(ids.add(record.id)) }
    }
    val draft = json.getJSONObject("draft")
    val target = if (draft.get("target") === JSONObject.NULL) null else draft.text("target")
    require(target == null || target in ids)
    val name = draft.text("name").also { require(it.length <= 80) }
    return ProgressionDocument(ProgressionDraft(name, decodeProgression(draft.getJSONObject("content")), target), records)
}
private fun encodeProgression(content: ProgressionContent): JSONObject = JSONObject()
    .put("capo", content.context.capo).put("tuning", JSONArray().apply { content.context.tuning.pitches.forEach {
        put(JSONObject().put("note", it.note.ordinal).put("octave", it.octave))
    } }).put("bpm", content.timing.bpm).put("numerator", content.timing.numerator)
    .put("denominator", content.timing.denominator.name).put("metronome", content.metronomeEnabled).put("loop", content.loop)
    .put("steps", JSONArray().apply { content.steps.forEach { step ->
        put(JSONObject().put("value", step.duration.value.name).put("dotted", step.duration.dotted).apply {
            when (step) {
                is ProgressionStep.Rest -> put("kind", "rest")
                is ProgressionStep.Chord -> {
                    put("kind", "chord").put("name", step.name).put("tie", step.tieToNext)
                    put("stops", JSONArray().apply { step.shape.stops.forEach { put(when (it) {
                        StringStop.Muted -> -1; StringStop.Open -> 0; is StringStop.Fretted -> it.fret
                    }) } })
                }
            }
        })
    } })
private fun decodeProgression(json: JSONObject): ProgressionContent {
    val tuning = json.getJSONArray("tuning").objects()
    require(tuning.size == 6)
    val context = GuitarContext(GuitarTuning(tuning.map {
        val note = it.int("note").also { value -> require(value in 0..11) }
        GuitarPitch(PitchClass.entries[note], it.int("octave"))
    }), json.int("capo"))
    val count = json.int("numerator").also { require(it in 1..16) }
    val timing = MetronomeConfig(json.int("bpm"), BeatUnit.valueOf(json.text("denominator")),
        List(count) { if (it == 0) BeatAccent.Accent else BeatAccent.Normal })
    val steps = json.getJSONArray("steps").objects().map {
        val duration = NoteDuration(NoteValue.valueOf(it.text("value")), it.flag("dotted"))
        when (it.text("kind")) {
            "rest" -> ProgressionStep.Rest(duration)
            "chord" -> {
                val stops = it.getJSONArray("stops")
                require(stops.length() == 6)
                val shape = ChordShape(List(6) { index ->
                    val value = stops.get(index).also { raw -> require(raw is Int) } as Int
                    when (value) { -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(value) }
                })
                ProgressionStep.Chord(it.text("name"), shape, duration, it.flag("tie"))
            }
            else -> throw IllegalArgumentException("Unknown progression step")
        }
    }
    return ProgressionContent(context, timing, json.flag("metronome"), json.flag("loop"), steps)
}
private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
private fun JSONObject.int(key: String): Int = get(key).let { require(it is Int); it }
private fun JSONObject.text(key: String): String = get(key).let { require(it is String); it }
private fun JSONObject.flag(key: String): Boolean = get(key).let { require(it is Boolean); it }
