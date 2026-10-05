package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.IntervalPresentation
import com.pekochan069.guitarlearner.domain.TrainingFailure
import com.pekochan069.guitarlearner.domain.TrainingInterval
import com.pekochan069.guitarlearner.domain.TrainingInstrument
import com.pekochan069.guitarlearner.domain.TrainingRepresentation
import com.pekochan069.guitarlearner.domain.TrainingSettings
import com.pekochan069.guitarlearner.domain.TrainingSubject

internal interface TrainingSettingsStore {
    fun read(): Either<TrainingFailure, TrainingSettings>
    fun write(settings: TrainingSettings): Either<TrainingFailure, Unit>
}

internal class TrainingStorage(private val preferences: SharedPreferences) : TrainingSettingsStore {
    private var acceptedSource: String? = null
    private var readable = false

    override fun read(): Either<TrainingFailure, TrainingSettings> = try {
        val source = preferences.getString(KEY, null)
        val settings = source?.let(::decodeTrainingSettings) ?: TrainingSettings()
        acceptedSource = source
        readable = true
        settings.right()
    } catch (_: IllegalArgumentException) {
        unreadable()
    } catch (_: ClassCastException) {
        unreadable()
    } catch (_: SecurityException) {
        unreadable()
    }

    private fun unreadable(): Either<TrainingFailure, TrainingSettings> {
        readable = false
        return TrainingFailure.SettingsReadFailed.left()
    }

    override fun write(settings: TrainingSettings): Either<TrainingFailure, Unit> {
        if (!readable) return TrainingFailure.SettingsReadFailed.left()
        val source = encodeTrainingSettings(settings)
        return try {
            if (preferences.commitString(KEY, source)) {
                acceptedSource = source
                Unit.right()
            } else {
                restoreAcceptedMemory()
                TrainingFailure.SettingsWriteFailed.left()
            }
        } catch (_: SecurityException) {
            restoreAcceptedMemory()
            TrainingFailure.SettingsWriteFailed.left()
        }
    }

    private fun restoreAcceptedMemory() {
        try {
            preferences.commitString(KEY, acceptedSource)
        } catch (_: SecurityException) {
        }
    }

    companion object { const val KEY: String = "settings_v1" }
}

internal fun encodeTrainingSettings(settings: TrainingSettings): String = listOf("2", settings.subject.name,
    settings.representation.name, settings.intervalPresentation.name,
    settings.intervals.sortedBy { it.semitones }.joinToString(",") { it.name }, settings.instrument.name).joinToString("|")

internal fun decodeTrainingSettings(source: String): TrainingSettings {
    val values = source.split('|')
    require(values.size == 5 && values[0] == "1" || values.size == 6 && values[0] == "2")
    val intervals = if (values[4].isEmpty()) emptyList() else values[4].split(',').map(TrainingInterval::valueOf)
    require(intervals.distinct().size == intervals.size)
    return TrainingSettings(TrainingSubject.valueOf(values[1]), TrainingRepresentation.valueOf(values[2]),
        IntervalPresentation.valueOf(values[3]), intervals.toSet(),
        if (values[0] == "1") TrainingInstrument.Piano else TrainingInstrument.valueOf(values[5]))
}
