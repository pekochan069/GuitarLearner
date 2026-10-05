package com.pekochan069.guitarlearner.presentation.logic

import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*

internal fun TrainingSnapshot.toUi(): TrainingUiState = TrainingUiState(
    settings = settings.toUi(),
    stage = when (val stage = stage) {
        TrainingStage.Setup -> TrainingStageUi.Setup
        is TrainingStage.Active -> stage.session.let { session -> TrainingStageUi.Question(
            key = session.key.toUi(), number = session.index + 1, settings = session.settings.toUi(),
            positions = session.question.positions.map { TrainingPositionUi(it.stringNumber, it.fret) },
            staff = session.question.positions.map { it.toStaffUi() },
            choices = if (session.settings.subject == TrainingSubject.Note) TrainingNoteUi.entries.map { TrainingAnswerUi.Note(it) }
                else session.settings.intervals.sortedBy { it.semitones }.map { TrainingAnswerUi.Interval(TrainingIntervalUi.entries[it.semitones]) },
            feedback = session.response?.toUi(),
        ) }
        is TrainingStage.Results -> TrainingStageUi.Results(stage.correctCount,
            stage.responses.mapIndexed { index, response -> TrainingResultUi(index + 1, response.toUi()) })
    },
    audio = when (val audio = audio) {
        TrainingAudioStatus.Idle -> TrainingAudioUi.Idle
        is TrainingAudioStatus.Preparing -> TrainingAudioUi.Preparing(TrainingSoundUi.valueOf(audio.sound.name))
        is TrainingAudioStatus.Playing -> TrainingAudioUi.Playing(TrainingSoundUi.valueOf(audio.sound.name))
        is TrainingAudioStatus.Failed -> TrainingAudioUi.Failed(TrainingSoundUi.valueOf(audio.sound.name), audio.failure.toUi())
    },
    settingsSaving = storage is TrainingStorageStatus.Saving,
    settingsNotice = (storage as? TrainingStorageStatus.Failed)?.failure?.toUi(),
    notice = notice?.toUi(),
)

internal fun TrainingEvent.toRequest(): TrainingRequest = when (this) {
    is TrainingEvent.SetSettings -> TrainingRequest.SetSettings(settings.toDomain())
    TrainingEvent.RetrySettings -> TrainingRequest.RetrySettings
    TrainingEvent.Start -> TrainingRequest.Start
    is TrainingEvent.Answer -> TrainingRequest.Answer(key.toDomain(), answer.toDomain())
    is TrainingEvent.Next -> TrainingRequest.Next(key.toDomain())
    is TrainingEvent.Replay -> TrainingRequest.Replay(key.toDomain(), TrainingSound.valueOf(sound.name))
    TrainingEvent.Exit -> TrainingRequest.Exit
}

private fun TrainingSettings.toUi(): TrainingSettingsUi = TrainingSettingsUi(TrainingSubjectUi.valueOf(subject.name),
    TrainingRepresentationUi.valueOf(representation.name), IntervalPresentationUi.valueOf(intervalPresentation.name),
    intervals.map { TrainingIntervalUi.entries[it.semitones] }.toSet())

private fun TrainingSettingsUi.toDomain(): TrainingSettings = TrainingSettings(TrainingSubject.valueOf(subject.name),
    TrainingRepresentation.valueOf(representation.name), IntervalPresentation.valueOf(intervalPresentation.name),
    intervals.map { TrainingInterval.entries[it.ordinal] }.toSet())

private fun TrainingQuestionKey.toUi(): TrainingQuestionKeyUi = TrainingQuestionKeyUi(sessionId, questionIndex)
private fun TrainingQuestionKeyUi.toDomain(): TrainingQuestionKey = TrainingQuestionKey(sessionId, questionIndex)
private fun TrainingAnswer.toUi(): TrainingAnswerUi = when (this) {
    is TrainingAnswer.Note -> TrainingAnswerUi.Note(TrainingNoteUi.entries[pitchClass.ordinal])
    is TrainingAnswer.Interval -> TrainingAnswerUi.Interval(TrainingIntervalUi.entries[interval.semitones])
}
private fun TrainingAnswerUi.toDomain(): TrainingAnswer = when (this) {
    is TrainingAnswerUi.Note -> TrainingAnswer.Note(PitchClass.entries[note.ordinal])
    is TrainingAnswerUi.Interval -> TrainingAnswer.Interval(TrainingInterval.entries[interval.ordinal])
}
private fun TrainingResponse.toUi(): TrainingFeedbackUi = TrainingFeedbackUi(chosen.toUi(), question.answer.toUi(), correct)
private fun TrainingFailure.toUi(): TrainingNoticeUi = when (this) {
    TrainingFailure.EmptyIntervalPool -> TrainingNoticeUi.EmptyIntervalPool
    TrainingFailure.SettingsReadFailed -> TrainingNoticeUi.SettingsReadFailed
    TrainingFailure.SettingsWriteFailed -> TrainingNoticeUi.SettingsWriteFailed
    is TrainingFailure.MetronomeStopFailed -> TrainingNoticeUi.MetronomeStopFailed
    TrainingFailure.PlaybackFailed -> TrainingNoticeUi.PlaybackFailed
    TrainingFailure.ShutdownFailed -> TrainingNoticeUi.ShutdownFailed
    TrainingFailure.FocusDenied -> TrainingNoticeUi.FocusDenied
    TrainingFailure.OutputInterrupted -> TrainingNoticeUi.OutputInterrupted
}

internal fun TrainingPosition.toStaffUi(): TrainingStaffNoteUi {
    val writtenMidi = midi + 12
    val pitchClass = writtenMidi % 12
    val letter = listOf(0, 0, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)[pitchClass]
    val accidental = when (pitchClass) {
        1, 6 -> TrainingAccidentalUi.Sharp
        3, 8, 10 -> TrainingAccidentalUi.Flat
        else -> TrainingAccidentalUi.Natural
    }
    return TrainingStaffNoteUi((writtenMidi / 12 - 1) * 7 + letter - 30, accidental)
}
