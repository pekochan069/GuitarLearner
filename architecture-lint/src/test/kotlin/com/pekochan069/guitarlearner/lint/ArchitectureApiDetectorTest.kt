package com.pekochan069.guitarlearner.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class ArchitectureApiDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ArchitectureApiDetector()
    override fun getIssues(): List<Issue> = listOf(ArchitectureApiDetector.ISSUE)

    fun testResolvedFileConstructor() {
        lint().projects(
            project().name("domain").files(
                kotlin("src/main/kotlin/example/Bad.kt", """
                    package example
                    import java.io.File as DiskFile
                    fun hidden() = DiskFile("secret")
                """).indented(),
            ),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expectErrorCount(2)
    }

    fun testPureFunction() {
        lint().projects(
            project().name("domain").files(
                kotlin("src/main/kotlin/example/Good.kt", """
                    package example
                    fun plus(left: Int, right: Int): Int = left + right
                """).indented(),
            ),
        ).allowMissingSdk().testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testQualifiedIoAndHelperGetter() {
        source("domain", """
            package example
            fun helper() = java.io.File("bad")
            val hidden get() = helper()
        """).expectErrorCount(3)
        source("ui", """
            package example
            val hidden get() = android.os.SystemClock.elapsedRealtime()
            fun render() = hidden
        """, clock).expectErrorCount(1)
    }

    fun testCallableNativeReference() {
        source("ui", """
            package example
            val hidden = android.os.SystemClock::elapsedRealtime
        """, clock).expectErrorCount(1)
    }

    fun testCoroutineAliasesAndGlobalScope() {
        source("app", """
            package example
            import kotlinx.coroutines.runBlocking as blocking
            import kotlinx.coroutines.CoroutineScope as detached
            import kotlinx.coroutines.MainScope as main
            import kotlinx.coroutines.GlobalScope as Global
            fun bad() {
                blocking {}
                detached()
                main()
                Global.toString()
            }
        """, coroutines).expectErrorCount(5)
    }

    fun testBroadCatchesAndRunCatching() {
        source("adapters", """
            package example
            import java.util.concurrent.CancellationException as Cancelled
            fun bad() {
                runCatching { 1 }
                try {} catch (error: Throwable) {}
                try {} catch (error: Exception) {}
                try {} catch (error: Cancelled) {}
                try {} catch (error: RuntimeException) {}
                try {} catch (error: Error) {}
            }
        """).expectErrorCount(6)
    }

    fun testPresenterEmissionAndUiEffects() {
        source("logic", """
            package example
            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            @Composable fun present() { Text("wrong layer") }
        """, composable, material3).expectErrorCount(1)
        source("ui", """
            package example
            import androidx.compose.runtime.SideEffect as Effect
            fun render() { Effect {} }
        """, effects).expectErrorCount(1)
    }

    fun testMaterial2AndDi() {
        source("ui", """
            package example
            import androidx.compose.material.Button
            fun render() { Button() }
        """, kotlin("""
            package androidx.compose.material
            fun Button() {}
        """).indented()).expectErrorCount(1)
        source("ui", """
            package example
            class Renderer @javax.inject.Inject constructor()
        """, java("""
            package javax.inject;
            public @interface Inject {}
        """).indented()).expectErrorCount(1)
    }

    fun testScreenOnlySerialization() {
        source("contract", """
            package example
            @kotlinx.parcelize.Parcelize
            object FoundationScreen : com.slack.circuit.runtime.screen.Screen
        """, parcelable, parcelize, screen).expectClean()
        source("contract", """
            package example
            class Leaked(val platform: android.os.Parcelable)
        """, parcelable).expectErrorCount(2)
    }

    fun testValidRenderAndAdapterIo() {
        source("ui", """
            package example
            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            @Composable fun render(text: String, eventSink: () -> Unit) {
                Text(text)
                eventSink()
            }
        """, composable, material3).expectClean()
        source("adapters", """
            package example
            fun location() = java.io.File("settings").path
        """).expectClean()
    }

    fun testArchitectureIssueCannotBeSuppressed() {
        source("domain", """
            @file:Suppress("ArchitectureApi", "all")
            package example
            fun bad() = java.io.File("bad")
        """).expectErrorCount(2)
    }

    fun testPreviewConstantsWithoutNativeEffectsOrShadowAllowance() {
        val configuration = java("""
            package android.content.res;
            public final class Configuration { public static final int UI_MODE_NIGHT_YES = 32; }
        """).indented()
        val preview = kotlin("""
            package androidx.compose.ui.tooling.preview
            @Target(AnnotationTarget.FUNCTION)
            annotation class Preview(val showBackground: Boolean = false, val uiMode: Int = 0)
        """).indented()
        source("ui", """
            package example
            import androidx.compose.ui.tooling.preview.Preview as ImagePreview
            @ImagePreview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
            fun darkPreview() {}
        """, configuration, preview).expectClean()
        source("ui", """
            package example
            @androidx.compose.ui.tooling.preview.Preview
            fun badPreview() { helper() }
            fun helper(): Long = hidden
            val hidden get() = android.os.SystemClock.elapsedRealtime()
        """, preview, clock).expectErrorCount(1)
        source("ui", """
            package example
            annotation class Preview(val uiMode: Int)
            @Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
            fun shadowPreview() {}
        """, configuration).expectErrorCount(1)
    }

    private fun source(module: String, code: String, vararg stubs: TestFile): TestLintResult =
        lint().projects(project().name(module).files(
            kotlin("src/main/kotlin/example/Source.kt", code).indented(), *stubs,
        )).allowMissingSdk().testModes(TestMode.DEFAULT).run()

    companion object {
        private val clock = java("""
            package android.os;
            public final class SystemClock { public static long elapsedRealtime() { return 0L; } }
        """).indented()
        private val coroutines = kotlin("""
            package kotlinx.coroutines
            object GlobalScope
            fun CoroutineScope(): Any = Any()
            fun MainScope(): Any = Any()
            fun runBlocking(block: () -> Unit): Unit = block()
        """).indented()
        private val composable = kotlin("""
            package androidx.compose.runtime
            @Target(AnnotationTarget.FUNCTION) annotation class Composable
        """).indented()
        private val effects = kotlin("""
            package androidx.compose.runtime
            fun SideEffect(effect: () -> Unit): Unit = effect()
        """).indented()
        private val material3 = kotlin("""
            package androidx.compose.material3
            fun Text(value: String) {}
        """).indented()
        private val parcelable = java("""
            package android.os;
            public interface Parcelable {}
        """).indented()
        private val parcelize = kotlin("""
            package kotlinx.parcelize
            @Target(AnnotationTarget.CLASS) annotation class Parcelize
        """).indented()
        private val screen = kotlin("""
            package com.slack.circuit.runtime.screen
            interface Screen : android.os.Parcelable
        """).indented()
    }
}
