package com.pekochan069.guitarlearner.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

enum class TrainingSubject { Note, Interval }
enum class TrainingRepresentation { Listening, Staff, Fretboard, Tab }
enum class IntervalPresentation { Ascending, Descending, Harmonic }
enum class TrainingInterval(val semitones: Int) {
    Unison(0), MinorSecond(1), MajorSecond(2), MinorThird(3), MajorThird(4), PerfectFourth(5),
    Tritone(6), PerfectFifth(7), MinorSixth(8), MajorSixth(9), MinorSeventh(10), MajorSeventh(11), Octave(12),
}

class TrainingSettings(
    val subject: TrainingSubject = TrainingSubject.Note,
    val representation: TrainingRepresentation = TrainingRepresentation.Listening,
    val intervalPresentation: IntervalPresentation = IntervalPresentation.Ascending,
    intervals: Set<TrainingInterval> = TrainingInterval.entries.toSet(),
) {
    val intervals: Set<TrainingInterval> = Collections.unmodifiableSet(intervals.toSet())

    fun copy(
        subject: TrainingSubject = this.subject,
        representation: TrainingRepresentation = this.representation,
        intervalPresentation: IntervalPresentation = this.intervalPresentation,
        intervals: Set<TrainingInterval> = this.intervals,
    ): TrainingSettings = TrainingSettings(subject, representation, intervalPresentation, intervals)

    override fun equals(other: Any?): Boolean = other is TrainingSettings && subject == other.subject &&
        representation == other.representation && intervalPresentation == other.intervalPresentation && intervals == other.intervals

    override fun hashCode(): Int = 31 * (31 * (31 * subject.hashCode() + representation.hashCode()) +
        intervalPresentation.hashCode()) + intervals.hashCode()
}

data class TrainingPosition(val stringIndex: Int, val fret: Int) {
    init { require(stringIndex in 0..5 && fret in 0..12) }
    val stringNumber: Int get() = 6 - stringIndex
    val midi: Int get() = TuningPreset.Standard.tuning.pitches[stringIndex].midi + fret
}

sealed interface TrainingQuestion {
    val positions: List<TrainingPosition>
    val answer: TrainingAnswer

    data class Note(val position: TrainingPosition) : TrainingQuestion {
        override val positions: List<TrainingPosition> get() = listOf(position)
        override val answer: TrainingAnswer get() = TrainingAnswer.Note(PitchClass.entries[position.midi % 12])
    }

    data class Interval(val lower: TrainingPosition, val upper: TrainingPosition) : TrainingQuestion {
        init { require(upper.midi - lower.midi in 0..12) }
        override val positions: List<TrainingPosition> get() = listOf(lower, upper)
        override val answer: TrainingAnswer get() = TrainingAnswer.Interval(TrainingInterval.entries[upper.midi - lower.midi])
    }
}

sealed interface TrainingAnswer {
    data class Note(val pitchClass: PitchClass) : TrainingAnswer
    data class Interval(val interval: TrainingInterval) : TrainingAnswer
}

data class TrainingQuestionKey(val sessionId: Long, val questionIndex: Int)
data class TrainingResponse(val question: TrainingQuestion, val chosen: TrainingAnswer) {
    val correct: Boolean get() = chosen == question.answer
}

class TrainingSession private constructor(
    private val id: Long,
    val settings: TrainingSettings,
    private val questions: List<TrainingQuestion>,
    val index: Int,
    responses: List<TrainingResponse>,
) {
    val responses: List<TrainingResponse> = Collections.unmodifiableList(responses.toList())
    val question: TrainingQuestion get() = questions[index]
    val key: TrainingQuestionKey get() = TrainingQuestionKey(id, index)
    val response: TrainingResponse? get() = responses.getOrNull(index)

    fun answer(key: TrainingQuestionKey, answer: TrainingAnswer): TrainingSession {
        if (key != this.key || response != null) return this
        val accepted = when (answer) {
            is TrainingAnswer.Note -> settings.subject == TrainingSubject.Note
            is TrainingAnswer.Interval -> settings.subject == TrainingSubject.Interval && answer.interval in settings.intervals
        }
        if (!accepted) return this
        return TrainingSession(id, settings, questions, index, responses + TrainingResponse(question, answer))
    }

    fun next(key: TrainingQuestionKey): TrainingStage {
        if (key != this.key || response == null) return TrainingStage.Active(this)
        return if (index == QUESTION_COUNT - 1) TrainingStage.Results(settings, responses)
        else TrainingStage.Active(TrainingSession(id, settings, questions, index + 1, responses))
    }

    companion object {
        const val QUESTION_COUNT: Int = 10

        fun start(id: Long, settings: TrainingSettings, choose: (Int) -> Int): Either<TrainingFailure, TrainingSession> {
            if (settings.subject == TrainingSubject.Interval && settings.intervals.isEmpty()) return TrainingFailure.EmptyIntervalPool.left()
            val questions = List(QUESTION_COUNT) { TrainingRules.question(settings, choose) }
            return TrainingSession(id, settings.copy(), questions, 0, emptyList()).right()
        }
    }
}

object TrainingRules {
    private val positions = (0..5).flatMap { string -> (0..12).map { fret -> TrainingPosition(string, fret) } }

    fun question(settings: TrainingSettings, choose: (Int) -> Int): TrainingQuestion = when (settings.subject) {
        TrainingSubject.Note -> TrainingQuestion.Note(positions[choose(positions.size)])
        TrainingSubject.Interval -> {
            require(settings.intervals.isNotEmpty())
            val intervals = settings.intervals.sortedBy { it.semitones }
            val interval = intervals[choose(intervals.size)]
            val pairs = positions.flatMap { lower -> positions.filter { it.midi - lower.midi == interval.semitones }
                .map { upper -> TrainingQuestion.Interval(lower, upper) } }
            pairs[choose(pairs.size)]
        }
    }
}

sealed interface TrainingStage {
    data object Setup : TrainingStage
    data class Active(val session: TrainingSession) : TrainingStage
    class Results(val settings: TrainingSettings, responses: List<TrainingResponse>) : TrainingStage {
        val responses: List<TrainingResponse> = Collections.unmodifiableList(responses.toList())
        init { require(this.responses.size == TrainingSession.QUESTION_COUNT) }
        val correctCount: Int get() = responses.count { it.correct }
    }
}

enum class TrainingSound { Question, Comparison }
sealed interface TrainingFailure {
    data object EmptyIntervalPool : TrainingFailure
    data object SettingsReadFailed : TrainingFailure
    data object SettingsWriteFailed : TrainingFailure
    data class MetronomeStopFailed(val cause: MetronomeFailure) : TrainingFailure
    data object PlaybackFailed : TrainingFailure
    data object ShutdownFailed : TrainingFailure
    data object FocusDenied : TrainingFailure
    data object OutputInterrupted : TrainingFailure
}

sealed interface TrainingStorageStatus {
    data object Ready : TrainingStorageStatus
    data class Saving(val requested: TrainingSettings) : TrainingStorageStatus
    data class Failed(val failure: TrainingFailure, val requested: TrainingSettings? = null) : TrainingStorageStatus
}

sealed interface TrainingAudioStatus {
    data object Idle : TrainingAudioStatus
    data class Preparing(val sound: TrainingSound) : TrainingAudioStatus
    data class Playing(val sound: TrainingSound) : TrainingAudioStatus
    data class Failed(val sound: TrainingSound, val failure: TrainingFailure) : TrainingAudioStatus
}

data class TrainingSnapshot(
    val settings: TrainingSettings = TrainingSettings(),
    val stage: TrainingStage = TrainingStage.Setup,
    val storage: TrainingStorageStatus = TrainingStorageStatus.Ready,
    val audio: TrainingAudioStatus = TrainingAudioStatus.Idle,
    val notice: TrainingFailure? = null,
)

sealed interface TrainingRequest {
    data class SetSettings(val settings: TrainingSettings) : TrainingRequest
    data object RetrySettings : TrainingRequest
    data object Start : TrainingRequest
    data class Answer(val key: TrainingQuestionKey, val answer: TrainingAnswer) : TrainingRequest
    data class Next(val key: TrainingQuestionKey) : TrainingRequest
    data class Replay(val key: TrainingQuestionKey, val sound: TrainingSound) : TrainingRequest
    data object Exit : TrainingRequest
}

interface Training {
    val current: StateFlow<TrainingSnapshot>
    fun submit(request: TrainingRequest)
}
