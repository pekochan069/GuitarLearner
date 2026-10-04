package com.pekochan069.guitarlearner.presentation.contract

enum class ChordSection { Lookup, Edit, Collection }
enum class ChordStopUi { Muted, Open, Fretted }
enum class ChordAnalysisUi { Empty, Note, Recognized, Unrecognized }
enum class ChordLookupUi { Idle, Searching, Ready, NoShapes }
enum class ChordNotice { InvalidInput, InvalidName, EmptyShape, CandidateMissing, RecordMissing, ReadFailed, WriteFailed }
enum class TuningPresetUi { Standard, DropD, Dadgad, OpenG, OpenD }

enum class ChordQualityUi(val symbol: String) {
    Major(""), Minor("m"), Power("5"), Diminished("dim"), Augmented("aug"), Sus2("sus2"), Sus4("sus4"),
    Sixth("6"), MinorSixth("m6"), SixNine("6/9"), MinorSixNine("m6/9"), Add9("add9"), MinorAdd9("madd9"),
    Seventh("7"), MajorSeventh("maj7"), MinorSeventh("m7"), MinorMajorSeventh("mMaj7"),
    DiminishedSeventh("dim7"), HalfDiminished("m7♭5"), SeventhSus2("7sus2"), SeventhSus4("7sus4"),
    Ninth("9"), MajorNinth("maj9"), MinorNinth("m9"), Eleventh("11"), MajorEleventh("maj11"),
    MinorEleventh("m11"), Thirteenth("13"), MajorThirteenth("maj13"), MinorThirteenth("m13"),
    SeventhFlat5("7♭5"), SeventhSharp5("7♯5"), SeventhFlat9("7♭9"), SeventhSharp9("7♯9"),
    NinthSharp11("9♯11"), ThirteenthFlat9("13♭9"),
}

data class ChordCandidateUi(val root: Int, val quality: ChordQualityUi, val symbol: String, val omitted: String, val selected: Boolean)

data class ChordStringUi(
    val index: Int,
    val stop: ChordStopUi,
    val fret: Int,
    val note: String?,
    val degree: String?,
    val tuning: String,
    val noteInput: String,
    val octaveInput: String,
    val fretInput: String,
    val noteError: Boolean,
    val octaveError: Boolean,
    val fretError: Boolean,
)

data class SavedChordUi(val id: String, val name: String, val symbol: String?, val analysis: ChordAnalysisUi,
    val notes: String, val tuning: String, val capo: Int)

data class ChordUiState(
    val section: ChordSection,
    val tuningExpanded: Boolean,
    val preset: TuningPresetUi?,
    val capoInput: String,
    val capo: Int,
    val capoError: Boolean,
    val strings: List<ChordStringUi>,
    val name: String,
    val nameError: Boolean,
    val targetName: String?,
    val analysis: ChordAnalysisUi,
    val soundingSymbol: String?,
    val shapeSymbol: String?,
    val notes: String,
    val candidates: List<ChordCandidateUi>,
    val roots: List<String>,
    val root: Int,
    val quality: ChordQualityUi,
    val lookup: ChordLookupUi,
    val lookupSymbol: String?,
    val lookupShapeSymbol: String?,
    val lookupStrings: List<ChordStringUi>,
    val lookupNotes: String,
    val lookupOmitted: String,
    val representativeIndex: Int,
    val representativeCount: Int,
    val records: List<SavedChordUi>,
    val canSave: Boolean,
    val busy: Boolean,
    val unsynced: Boolean,
    val readFailed: Boolean,
    val notice: ChordNotice?,
)

sealed interface ChordEvent {
    data class SetSection(val value: ChordSection) : ChordEvent
    data class SetTuningExpanded(val value: Boolean) : ChordEvent
    data class SetPreset(val value: TuningPresetUi) : ChordEvent
    data class SetCapo(val value: String) : ChordEvent
    data class SetNote(val index: Int, val value: String) : ChordEvent
    data class SetOctave(val index: Int, val value: String) : ChordEvent
    data class SetStop(val index: Int, val value: ChordStopUi) : ChordEvent
    data class SetFret(val index: Int, val value: String) : ChordEvent
    data class SetName(val value: String) : ChordEvent
    data class SelectCandidate(val root: Int, val quality: ChordQualityUi) : ChordEvent
    data class SetRoot(val value: Int) : ChordEvent
    data class SetQuality(val value: ChordQualityUi) : ChordEvent
    data object Search : ChordEvent
    data class SelectRepresentative(val index: Int) : ChordEvent
    data object CopyRepresentative : ChordEvent
    data object NewDraft : ChordEvent
    data object Save : ChordEvent
    data class Load(val id: String) : ChordEvent
    data class Delete(val id: String) : ChordEvent
    data object RetryDraftWrite : ChordEvent
    data object RetryStorageRead : ChordEvent
}
