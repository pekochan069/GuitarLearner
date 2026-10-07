package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.LearningFailure
import com.pekochan069.guitarlearner.domain.LearningProgress
import com.pekochan069.guitarlearner.domain.LessonId

internal interface LearningProgressStore {
    fun read(): Either<LearningFailure, LearningProgress>
    fun write(progress: LearningProgress): Either<LearningFailure, Unit>
}

internal class LearningStorage(private val preferences: SharedPreferences) : LearningProgressStore {
    private var acceptedSource: String? = null
    private var readable = false

    override fun read(): Either<LearningFailure, LearningProgress> = try {
        val source = preferences.getString(KEY, null)
        val progress = source?.let(::decodeLearningProgress) ?: LearningProgress()
        acceptedSource = source
        readable = true
        progress.right()
    } catch (_: IllegalArgumentException) {
        unreadable()
    } catch (_: ClassCastException) {
        unreadable()
    } catch (_: SecurityException) {
        unreadable()
    }

    private fun unreadable(): Either<LearningFailure, LearningProgress> {
        readable = false
        return LearningFailure.ReadFailed.left()
    }

    override fun write(progress: LearningProgress): Either<LearningFailure, Unit> {
        if (!readable) return LearningFailure.ReadFailed.left()
        val source = encodeLearningProgress(progress)
        return try {
            if (preferences.commitString(KEY, source)) {
                acceptedSource = source
                Unit.right()
            } else failedWrite()
        } catch (_: SecurityException) {
            failedWrite()
        }
    }

    private fun failedWrite(): Either<LearningFailure, Unit> {
        try {
            preferences.commitString(KEY, acceptedSource)
        } catch (_: SecurityException) {
        }
        return LearningFailure.WriteFailed.left()
    }

    companion object { const val KEY: String = "progress_v1" }
}

internal fun encodeLearningProgress(progress: LearningProgress): String = listOf(
    "1", progress.lastViewed?.savedId.orEmpty(), progress.completed.sortedBy { it.savedId }.joinToString(",") { it.savedId },
).joinToString("|")

internal fun decodeLearningProgress(source: String): LearningProgress {
    val values = source.split('|')
    require(values.size == 3 && values[0] == "1")
    fun lesson(id: String): LessonId = requireNotNull(LessonId.entries.firstOrNull { it.savedId == id })
    val completed = if (values[2].isEmpty()) emptyList() else values[2].split(',').map(::lesson)
    require(completed.distinct().size == completed.size)
    return LearningProgress(completed.toSet(), values[1].takeIf { it.isNotEmpty() }?.let(::lesson))
}
