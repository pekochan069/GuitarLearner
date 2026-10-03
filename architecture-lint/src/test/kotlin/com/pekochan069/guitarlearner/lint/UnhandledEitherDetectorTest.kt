package com.pekochan069.guitarlearner.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class UnhandledEitherDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = UnhandledEitherDetector()
    override fun getIssues(): List<Issue> = listOf(UnhandledEitherDetector.ISSUE)

    fun testDiscardedSuspendCall() {
        source("""
            package example
            suspend fun load(): arrow.core.Either<String, Int> = arrow.core.Either.Right(1)
            suspend fun ignored() { load() }
        """).expectErrorCount(1)
    }

    fun testDiscardedSealedConstructorsAndUnreadConstructorAlias(): Unit {
        source("""
            package example
            fun ignored() {
                arrow.core.Either.Left("failure")
                val unread = arrow.core.Either.Right(1)
            }
        """).expectErrorCount(2)
    }

    fun testUnitCoercedLambdaResult() {
        source("""
            package example
            fun load(): arrow.core.Either<String, Int> = arrow.core.Either.Right(1)
            fun launch(block: () -> Unit): Unit = block()
            fun ignored() {
                launch { load() }
                val callback: () -> Unit = { load() }
                callback()
            }
        """).expectErrorCount(2)
    }

    fun testUnusedAndUnconsumedAliases() {
        source("""
            package example
            suspend fun load(): arrow.core.Either<String, Int> = arrow.core.Either.Right(1)
            suspend fun ignored() {
                val unused = load()
                val first = load()
                val alias = first
                alias
            }
        """).expectErrorCount(2)
    }

    fun testReturnFoldBindExhaustiveWhenAndProductiveUse() {
        source("""
            package example
            import arrow.core.Either
            import arrow.core.Raise
            suspend fun load(): Either<String, Int> = Either.Right(1)
            suspend fun returned(): Either<String, Int> = load()
            suspend fun explicit(): Either<String, Int> { return load() }
            suspend fun folded(): Int {
                val result = load()
                return result.fold({ -1 }, { it })
            }
            suspend fun bound(scope: Raise<String>): Int = with(scope) { load().bind() }
            suspend fun handled(): Int = when (val result = load()) {
                is Either.Left -> -1
                is Either.Right -> result.value
            }
            suspend fun nullable(): Int? = load().getOrNull()
        """).expectClean()
    }

    fun testSuspendResultInsideUnitCoercedCoroutineLambda() {
        source("""
            package example
            suspend fun load(): arrow.core.Either<String, Int> = arrow.core.Either.Right(1)
            fun launch(block: suspend () -> Unit) {}
            fun ignored() { launch { load() } }
        """).expectErrorCount(1)
    }

    fun testUnitFoldAndUnitExhaustiveWhenAreConsumed() {
        source("""
            package example
            import arrow.core.Either
            fun load(): Either<String, Int> = Either.Right(1)
            fun launch(block: () -> Unit): Unit = block()
            fun consumed() {
                launch { load().fold({}, {}) }
                when (val result = load()) {
                    is Either.Left -> Unit
                    is Either.Right -> Unit
                }
            }
        """).expectClean()
    }

    fun testIssueCannotBeSuppressed() {
        source("""
            @file:Suppress("UnhandledEither", "all")
            package example
            fun load(): arrow.core.Either<String, Int> = arrow.core.Either.Right(1)
            fun ignored() { load() }
        """).expectErrorCount(1)
    }

    private fun source(code: String): TestLintResult = lint().projects(
        project().name("logic").files(
            kotlin("src/main/kotlin/example/Source.kt", code).indented(), either,
        ),
    ).allowMissingSdk().testModes(TestMode.DEFAULT).run()

    companion object {
        private val either = kotlin("""
            package arrow.core
            sealed class Either<out L, out R> {
                data class Left<L>(val value: L) : Either<L, Nothing>()
                data class Right<R>(val value: R) : Either<Nothing, R>()
                fun <T> fold(ifLeft: (L) -> T, ifRight: (R) -> T): T = when (this) {
                    is Left -> ifLeft(value)
                    is Right -> ifRight(value)
                }
                fun getOrNull(): R? = when (this) {
                    is Left -> null
                    is Right -> value
                }
            }
            interface Raise<L> {
                fun <R> Either<L, R>.bind(): R
            }
        """).indented()
    }
}
