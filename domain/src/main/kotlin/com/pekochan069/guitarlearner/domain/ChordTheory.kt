package com.pekochan069.guitarlearner.domain

import java.util.Collections

object ChordTheory {
    private val rootLetters = listOf(0, 0, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)
    private val letters = listOf("C", "D", "E", "F", "G", "A", "B")
    private val naturals = listOf(0, 2, 4, 5, 7, 9, 11)

    private fun formula(source: String, vararg optional: Int): ChordFormula = ChordFormula(source.split(' ').map { token ->
        val alteration = token.count { it == 'b' } * -1 + token.count { it == '#' }
        val number = token.trimStart('b', '#').toInt()
        ChordDegree(number, alteration, (naturals[(number - 1) % 7] + alteration + 12) % 12)
    }, optional.toSet())

    private val formulas = listOf(
        formula("1 3 5"), formula("1 b3 5"), formula("1 5"), formula("1 b3 b5"), formula("1 3 #5"),
        formula("1 2 5"), formula("1 4 5"), formula("1 3 5 6"), formula("1 b3 5 6"),
        formula("1 3 5 6 9"), formula("1 b3 5 6 9"), formula("1 3 5 9"), formula("1 b3 5 9"),
        formula("1 3 5 b7", 5), formula("1 3 5 7", 5), formula("1 b3 5 b7", 5), formula("1 b3 5 7", 5),
        formula("1 b3 b5 bb7"), formula("1 b3 b5 b7"), formula("1 2 5 b7", 5), formula("1 4 5 b7", 5),
        formula("1 3 5 b7 9", 5), formula("1 3 5 7 9", 5), formula("1 b3 5 b7 9", 5),
        formula("1 3 5 b7 9 11", 5, 9), formula("1 3 5 7 9 11", 5, 9), formula("1 b3 5 b7 9 11", 5, 9),
        formula("1 3 5 b7 9 11 13", 5, 9, 11), formula("1 3 5 7 9 11 13", 5, 9, 11),
        formula("1 b3 5 b7 9 11 13", 5, 9, 11), formula("1 3 b5 b7"), formula("1 3 #5 b7"),
        formula("1 3 5 b7 b9", 5), formula("1 3 5 b7 #9", 5), formula("1 3 5 b7 9 #11", 5),
        formula("1 3 5 b7 b9 11 13", 5, 11),
    )

    fun formula(quality: ChordQuality): ChordFormula = formulas[quality.ordinal]

    fun tones(context: GuitarContext, shape: ChordShape): List<Int?> = Collections.unmodifiableList(
        shape.stops.mapIndexed { index, stop ->
            when (stop) {
                StringStop.Muted -> null
                StringStop.Open -> context.tuning.pitches[index].midi + context.capo
                is StringStop.Fretted -> context.tuning.pitches[index].midi + context.capo + stop.fret
            }
        },
    )

    fun analyze(context: GuitarContext, shape: ChordShape): ChordAnalysis {
        val pitches = tones(context, shape).filterNotNull()
        if (pitches.isEmpty()) return ChordAnalysis.Empty
        val classes = pitches.map { it % 12 }.toSet()
        val bass = PitchClass.entries[pitches.min() % 12]
        if (classes.size == 1) return ChordAnalysis.Note(pitches.min())
        val candidates = ChordQuality.entries.flatMap { quality ->
            PitchClass.entries.mapNotNull { root -> match(ChordIdentity(root, quality), classes, bass) }
        }.sortedWith(compareBy<ChordCandidate> { it.omitted.size }
            .thenBy { it.bass != it.identity.root }.thenBy { it.identity.quality.ordinal }.thenBy { it.identity.root.ordinal })
        return if (candidates.isEmpty()) ChordAnalysis.Unrecognized
        else ChordAnalysis.Recognized(Collections.unmodifiableList(candidates))
    }

    fun normalize(draft: ChordDraft): ChordDraft {
        val analysis = analyze(draft.context, draft.shape)
        val candidates = (analysis as? ChordAnalysis.Recognized)?.candidates.orEmpty()
        val selected = candidates.firstOrNull { it.identity == draft.selected } ?: candidates.firstOrNull()
        return draft.copy(selected = selected?.identity)
    }

    fun selectedCandidate(draft: ChordDraft): ChordCandidate? =
        (analyze(draft.context, draft.shape) as? ChordAnalysis.Recognized)?.candidates?.firstOrNull { it.identity == draft.selected }

    fun shapeCandidate(candidate: ChordCandidate, capo: Int): ChordCandidate = candidate.copy(
        identity = candidate.identity.copy(root = candidate.identity.root.transpose(-capo)),
        bass = candidate.bass.transpose(-capo),
    )

    fun symbol(candidate: ChordCandidate): String = candidate.identity.root.symbol + candidate.identity.quality.symbol +
        if (candidate.bass == candidate.identity.root) "" else "/" + noteName(candidate.identity, candidate.bass)

    fun degree(identity: ChordIdentity, pitch: PitchClass): ChordDegree? = formula(identity.quality).degrees
        .firstOrNull { identity.root.transpose(it.semitones) == pitch }

    fun noteName(identity: ChordIdentity?, pitch: PitchClass): String {
        val role = identity?.let { degree(it, pitch) } ?: return pitch.symbol
        val letter = (rootLetters[identity.root.ordinal] + role.number - 1) % 7
        val accidental = Math.floorMod(pitch.ordinal - naturals[letter] + 6, 12) - 6
        return letters[letter] + when {
            accidental < 0 -> "♭".repeat(-accidental)
            else -> "♯".repeat(accidental)
        }
    }

    fun pitchName(identity: ChordIdentity?, midi: Int): String {
        val name = noteName(identity, PitchClass.entries[Math.floorMod(midi, 12)])
        val natural = naturals[letters.indexOf(name.take(1))]
        val accidental = name.count { it == '♯' } - name.count { it == '♭' }
        val octave = (midi - natural - accidental) / 12 - 1
        return name + octave
    }

    fun representatives(query: ChordQuery): List<ChordShape> {
        val formula = formula(query.identity.quality)
        val allowed = formula.degrees.fold(0) { mask, degree -> mask or (1 shl query.identity.root.transpose(degree.semitones).ordinal) }
        val required = formula.degrees.filter { it.number !in formula.optional }.fold(0) { mask, degree ->
            mask or (1 shl query.identity.root.transpose(degree.semitones).ordinal)
        }
        val found = HashSet<ChordShape>()
        val ranked = mutableListOf<Pair<ChordShape, ChordCandidate>>()
        val order = compareBy<Pair<ChordShape, ChordCandidate>> { it.second.omitted.size }
            .thenBy { it.second.bass != query.identity.root }
            .thenBy { pair -> frets(pair.first).let { if (it.isEmpty()) 0 else it.max() - it.min() } }
            .thenBy { pair -> frets(pair.first).maxOrNull() ?: 0 }
            .thenBy { pair -> pair.first.stops.count { it == StringStop.Muted } }
            .thenBy { pair -> pair.first.stops.joinToString(",") { stopCode(it).toString().padStart(2, '0') } }
        for (start in 1..9) {
            val options = query.context.tuning.pitches.map { pitch ->
                buildList {
                    add(StringStop.Muted)
                    if (allowed and (1 shl ((pitch.midi + query.context.capo) % 12)) != 0) add(StringStop.Open)
                    for (fret in start..start + 3) {
                        if (allowed and (1 shl ((pitch.midi + query.context.capo + fret) % 12)) != 0) add(StringStop.Fretted(fret))
                    }
                }
            }
            val remaining = IntArray(7)
            for (index in 5 downTo 0) {
                remaining[index] = remaining[index + 1] or options[index].fold(0) { mask, stop ->
                    if (stop == StringStop.Muted) mask else mask or (1 shl (pitch(query.context, index, stop) % 12))
                }
            }
            val stops = MutableList<StringStop>(6) { StringStop.Muted }
            fun visit(index: Int, mask: Int, bass: Int) {
                if (required and (mask or remaining[index]) != required) return
                if (index == 6) {
                    val classes = (0..11).filter { mask and (1 shl it) != 0 }.toSet()
                    val candidate = match(query.identity, classes, PitchClass.entries[bass % 12]) ?: return
                    val shape = ChordShape(stops)
                    if (found.add(shape)) {
                        ranked.add(shape to candidate)
                        ranked.sortWith(order)
                        if (ranked.size > 12) ranked.removeAt(12)
                    }
                    return
                }
                for (stop in options[index]) {
                    stops[index] = stop
                    if (stop == StringStop.Muted) visit(index + 1, mask, bass)
                    else {
                        val sounding = pitch(query.context, index, stop)
                        visit(index + 1, mask or (1 shl (sounding % 12)), minOf(bass, sounding))
                    }
                }
            }
            visit(0, 0, Int.MAX_VALUE)
        }
        return Collections.unmodifiableList(ranked.map { it.first })
    }

    private fun pitch(context: GuitarContext, index: Int, stop: StringStop): Int =
        context.tuning.pitches[index].midi + context.capo + stopCode(stop)

    private fun stopCode(stop: StringStop): Int = when (stop) {
        StringStop.Muted -> -1
        StringStop.Open -> 0
        is StringStop.Fretted -> stop.fret
    }

    private fun frets(shape: ChordShape): List<Int> = shape.stops.filterIsInstance<StringStop.Fretted>().map { it.fret }

    private fun match(identity: ChordIdentity, classes: Set<Int>, bass: PitchClass): ChordCandidate? {
        val formula = formula(identity.quality)
        val degrees = formula.degrees.associateBy { identity.root.transpose(it.semitones).ordinal }
        if (identity.root.ordinal !in classes || classes.any { it !in degrees }) return null
        val omitted = formula.degrees.filter { identity.root.transpose(it.semitones).ordinal !in classes }
        if (omitted.any { it.number !in formula.optional }) return null
        return ChordCandidate(identity, bass, Collections.unmodifiableList(omitted))
    }
}
