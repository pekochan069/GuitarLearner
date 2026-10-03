package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import android.annotation.SuppressLint
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomePreset
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class MetronomeDocument(
    val selected: MetronomeConfig = MetronomeConfig(),
    val presets: List<MetronomePreset> = emptyList(),
)

internal class MetronomeStorage(
    private val preferences: SharedPreferences,
    private val decode: (String) -> MetronomeDocument = ::decodeMetronome,
    private val encode: (MetronomeDocument) -> String = ::encodeMetronome,
) {
    private var acceptedSource: String? = null
    val initial: Either<MetronomeFailure, MetronomeDocument> = read()

    private fun read(): Either<MetronomeFailure, MetronomeDocument> = try {
        val source = preferences.getString(KEY, null)
        val document = source?.let(decode) ?: MetronomeDocument()
        acceptedSource = source
        document.right()
    } catch (_: JSONException) {
        MetronomeFailure.ReadFailed.left()
    } catch (_: IllegalArgumentException) {
        MetronomeFailure.ReadFailed.left()
    } catch (_: ClassCastException) {
        MetronomeFailure.ReadFailed.left()
    } catch (_: SecurityException) {
        MetronomeFailure.ReadFailed.left()
    }

    fun save(document: MetronomeDocument): Either<MetronomeFailure, Unit> {
        if (initial.isLeft()) return MetronomeFailure.ReadFailed.left()
        val source = encode(document)
        return try {
            if (commitSource(source)) {
                acceptedSource = source
                Unit.right()
            } else {
                restoreAcceptedMemory()
                MetronomeFailure.WriteFailed.left()
            }
        } catch (_: SecurityException) {
            restoreAcceptedMemory()
            MetronomeFailure.WriteFailed.left()
        }
    }

    private fun restoreAcceptedMemory() {
        try {
            // A failed commit also mutates SharedPreferences memory; restore the accepted document.
            if (!commitSource(acceptedSource)) Log.w("MetronomeStorage", "Disk rollback failed; accepted preference memory restored")
        } catch (_: SecurityException) {
            // Accepted state remains authoritative when storage access itself is unavailable.
        }
    }

    // The KTX edit helper returns Unit and cannot acknowledge a checked durable write.
    @SuppressLint("UseKtx")
    private fun commitSource(source: String?): Boolean {
        val editor = preferences.edit()
        if (source == null) editor.remove(KEY) else editor.putString(KEY, source)
        return editor.commit()
    }

    companion object { const val KEY: String = "document" }
}

internal fun encodeMetronome(document: MetronomeDocument): String = JSONObject()
    .put("version", 1)
    .put("selected", encodeConfig(document.selected))
    .put("presets", JSONArray().apply {
        document.presets.forEach { preset ->
            put(JSONObject().put("name", preset.name).put("config", encodeConfig(preset.config)))
        }
    }).toString()

internal fun decodeMetronome(source: String): MetronomeDocument {
    val root = JSONObject(source)
    require(integer(root, "version") == 1)
    val selected = decodeConfig(root.getJSONObject("selected"))
    val presets = root.getJSONArray("presets")
    val names = mutableSetOf<String>()
    val decoded = List(presets.length()) { index ->
        val entry = presets.getJSONObject(index)
        val name = entry.get("name")
        require(name is String)
        require(name.isNotBlank() && name == name.trim() && names.add(name))
        MetronomePreset(name, decodeConfig(entry.getJSONObject("config")))
    }
    return MetronomeDocument(selected, decoded)
}

private fun encodeConfig(config: MetronomeConfig): JSONObject = JSONObject()
    .put("bpm", config.bpm)
    .put("denominator", config.denominator.denominator)
    .put("beats", JSONArray().apply { config.beats.forEach { put(it.name) } })

private fun decodeConfig(json: JSONObject): MetronomeConfig {
    val denominator = requireNotNull(BeatUnit.entries.firstOrNull { it.denominator == integer(json, "denominator") })
    val beats = json.getJSONArray("beats")
    return MetronomeConfig(integer(json, "bpm"), denominator, List(beats.length()) {
        BeatAccent.valueOf(beats.getString(it))
    })
}

private fun integer(json: JSONObject, name: String): Int {
    val value = json.get(name)
    require(value is Int)
    return value
}
