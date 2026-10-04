package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TuningTolerance

internal interface TunerToleranceStore {
    fun read(): Either<TunerFailure, TuningTolerance>
    fun write(value: TuningTolerance): Either<TunerFailure, Unit>
}

internal class TunerToleranceStorage(private val preferences: SharedPreferences) : TunerToleranceStore {
    private var acceptedSource: String? = null
    private var readable = false

    override fun read(): Either<TunerFailure, TuningTolerance> = try {
        val source = preferences.getString(KEY, null)
        val tolerance = if (source == null) TuningTolerance.Normal else
            TuningTolerance.entries.firstOrNull { it.cents.toString() == source }
        if (tolerance == null) {
            readable = false
            TunerFailure.ToleranceReadFailed.left()
        } else {
            acceptedSource = source
            readable = true
            tolerance.right()
        }
    } catch (_: ClassCastException) {
        readable = false
        TunerFailure.ToleranceReadFailed.left()
    } catch (_: SecurityException) {
        readable = false
        TunerFailure.ToleranceReadFailed.left()
    }

    override fun write(value: TuningTolerance): Either<TunerFailure, Unit> {
        if (!readable) return TunerFailure.ToleranceReadFailed.left()
        val source = value.cents.toString()
        return try {
            if (commit(source)) {
                acceptedSource = source
                Unit.right()
            } else {
                restoreAcceptedMemory()
                TunerFailure.ToleranceWriteFailed.left()
            }
        } catch (_: SecurityException) {
            restoreAcceptedMemory()
            TunerFailure.ToleranceWriteFailed.left()
        }
    }

    private fun restoreAcceptedMemory() {
        try {
            commit(acceptedSource)
        } catch (_: SecurityException) {
        }
    }

    private fun commit(source: String?): Boolean = preferences.commitString(KEY, source)

    companion object { const val KEY: String = "tolerance_cents" }
}
