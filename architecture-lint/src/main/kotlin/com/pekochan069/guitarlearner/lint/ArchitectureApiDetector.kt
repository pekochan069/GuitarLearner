package com.pekochan069.guitarlearner.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import org.jetbrains.uast.*

class ArchitectureApiDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UCallExpression::class.java, USimpleNameReferenceExpression::class.java,
        UCallableReferenceExpression::class.java, UTypeReferenceExpression::class.java,
        UAnnotation::class.java, UCatchClause::class.java, UVariable::class.java,
        UMethod::class.java, UClass::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        val module = context.productionModule() ?: return null
        val reported = mutableSetOf<Int>()
        fun report(node: UElement, reason: String) {
            val location = context.getLocation(node)
            val offset = location.start?.offset ?: return
            if (reported.add(offset)) context.report(ISSUE, node, location, reason)
        }
        fun check(node: UElement, symbol: String, call: Boolean = false) {
            val reason = forbiddenReason(module, symbol, call) ?: return
            if (module == ProductionModule.CONTRACT && symbol in SCREEN_SERIALIZATION) {
                val cls = (node as? UClass) ?: node.getParentOfType<UClass>()
                if (cls != null && context.evaluator.implementsInterface(cls.javaPsi, "com.slack.circuit.runtime.screen.Screen", false)) return
            }
            report(node, reason)
        }
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                node.resolve()?.qualifiedSymbolName()?.let { check(node, it, true) }
            }
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.uastParent is UCallExpression || node.uastParent is UCallableReferenceExpression) return
                val target = node.resolve() ?: return
                val symbol = target.qualifiedSymbolName() ?: return
                if (module == ProductionModule.UI && symbol.startsWith("android.") &&
                    target is PsiField && target.computeConstantValue() != null &&
                    node.getParentOfType<UAnnotation>()?.qualifiedName == "androidx.compose.ui.tooling.preview.Preview") return
                if (target !is PsiClass || symbol.startsWith("kotlinx.coroutines.GlobalScope")) check(node, symbol)
            }
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                node.resolve()?.qualifiedSymbolName()?.let { check(node, it, true) }
            }
            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                node.type.referencedClasses().forEach { check(node, it) }
            }
            override fun visitVariable(node: UVariable) {
                node.type.referencedClasses().forEach { check(node, it) }
            }
            override fun visitMethod(node: UMethod) {
                node.returnType?.referencedClasses()?.forEach { check(node, it) }
            }
            override fun visitClass(node: UClass) {
                node.javaPsi.superTypes.flatMap { it.referencedClasses() }.forEach { check(node, it) }
            }
            override fun visitAnnotation(node: UAnnotation) {
                node.qualifiedName?.let { check(node, it) }
            }
            override fun visitCatchClause(node: UCatchClause) {
                node.typeReferences.forEach { type ->
                    if (type.type.referencedClasses().any { it in BROAD_CATCHES }) {
                        report(type, "Catch only known expected failures; propagate cancellation and unexpected defects")
                    }
                }
            }
        }
    }

    private fun forbiddenReason(module: ProductionModule, symbol: String, call: Boolean): String? {
        if (symbol.startsWith("androidx.compose.material.")) return "Use Material 3 Expressive instead of $symbol"
        if (symbol.startsWith("kotlinx.coroutines.GlobalScope") ||
            symbol.startsWith("kotlinx.coroutines.") && symbol.substringAfterLast('.') in setOf("runBlocking", "CoroutineScope", "MainScope") && call ||
            symbol.startsWith("kotlin.") && symbol.substringAfterLast('.') == "runCatching") {
            return "Use structured coroutines and explicit expected-error handling instead of $symbol"
        }
        if (module in setOf(ProductionModule.ADAPTERS, ProductionModule.APP)) return null
        if (IO_PREFIXES.any(symbol::startsWith) || symbol in SYSTEM_EFFECTS ||
            symbol.startsWith("java.lang.Thread") || symbol.startsWith("java.util.concurrent.Executors") || symbol.startsWith("kotlin.random.")) {
            return "Direct I/O/platform effects belong in adapters: $symbol"
        }
        if (DI_PREFIXES.any(symbol::startsWith)) return "Dependency injection belongs in the app composition root: $symbol"
        if (module == ProductionModule.DOMAIN && (symbol.startsWith("android.") || symbol.startsWith("androidx."))) {
            return "Domain code must not depend on Android or Compose: $symbol"
        }
        if (module == ProductionModule.CONTRACT && (symbol.startsWith("android.") || symbol.startsWith("androidx.") ||
            symbol.startsWith("kotlinx.coroutines.") || symbol.startsWith("kotlinx.parcelize.") || PRODUCT_IMPLEMENTATIONS.any(symbol::startsWith))) {
            return "Contract types must be independent; only Screen serialization may use Android: $symbol"
        }
        if (module == ProductionModule.LOGIC && (symbol.startsWith("android.") || NATIVE_PREFIXES.any(symbol::startsWith) ||
            RENDERING_PREFIXES.any(symbol::startsWith) || symbol.startsWith("com.pekochan069.guitarlearner.ui."))) {
            return "Presenters compute state and invoke domain capabilities; native APIs and UI emission are forbidden: $symbol"
        }
        if (module == ProductionModule.UI && (symbol.startsWith("android.") || PRODUCT_IMPLEMENTATIONS.any(symbol::startsWith) ||
            symbol.startsWith("com.slack.circuit.runtime.presenter.") || NATIVE_PREFIXES.any(symbol::startsWith) ||
            symbol.startsWith("kotlinx.coroutines.") || symbol.substringAfterLast('.') in COMPOSE_EFFECTS && symbol.startsWith("androidx.compose.runtime."))) {
            return "Rendering receives contract state/events; service access and effect execution are forbidden: $symbol"
        }
        if (module == ProductionModule.DOMAIN && symbol.startsWith("kotlinx.coroutines.") && call) {
            return "Domain functions must not execute coroutine effects: $symbol"
        }
        return null
    }

    companion object {
        private val IO_PREFIXES = listOf("java.io.", "java.nio.file.", "java.nio.channels.", "java.net.", "kotlin.io.", "java.lang.reflect.")
        private val DI_PREFIXES = listOf("dev.zacsweers.metro.", "dagger.", "javax.inject.", "org.koin.", "com.google.dagger.")
        private val NATIVE_PREFIXES = listOf("android.content.Context", "android.content.SharedPreferences", "android.app.Service", "android.media.", "android.hardware.", "android.database.", "androidx.appcompat.", "androidx.core.content.", "androidx.lifecycle.ViewModel", "androidx.lifecycle.viewmodel.", "androidx.activity.ComponentActivity", "androidx.activity.result.")
        private val RENDERING_PREFIXES = listOf("androidx.compose.ui.", "androidx.compose.foundation.", "androidx.compose.material3.", "com.slack.circuit.foundation.")
        private val PRODUCT_IMPLEMENTATIONS = listOf("com.pekochan069.guitarlearner.domain.", "com.pekochan069.guitarlearner.presentation.logic.", "com.pekochan069.guitarlearner.adapters.")
        private val SCREEN_SERIALIZATION = setOf("android.os.Parcelable", "kotlinx.parcelize.Parcelize")
        private val BROAD_CATCHES = setOf("java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException", "java.lang.Error", "kotlin.Throwable", "kotlin.Exception", "kotlin.RuntimeException", "kotlin.Error", "java.util.concurrent.CancellationException", "kotlinx.coroutines.CancellationException")
        private val SYSTEM_EFFECTS = setOf("java.lang.System.currentTimeMillis", "java.lang.System.nanoTime", "java.lang.System.getenv", "java.lang.System.getProperty", "java.lang.System.exit", "java.lang.System.out", "java.lang.System.err", "java.lang.System.`in`")
        private val COMPOSE_EFFECTS = setOf("LaunchedEffect", "DisposableEffect", "SideEffect", "produceState", "rememberCoroutineScope")
        val ISSUE: Issue = Issue.create(
            id = "ArchitectureApi", briefDescription = "Forbidden architecture API",
            explanation = "Resolved production APIs must obey module boundaries. Native effects belong in adapters, rendering receives state/events, and all modules propagate cancellation.",
            category = Category.CORRECTNESS, priority = 8, severity = Severity.ERROR,
            implementation = Implementation(ArchitectureApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
            suppressAnnotations = emptyList(),
        ).setAndroidSpecific(false)
    }
}
