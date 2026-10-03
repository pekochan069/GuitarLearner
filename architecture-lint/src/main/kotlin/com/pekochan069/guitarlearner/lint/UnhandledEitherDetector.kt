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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType

class UnhandledEitherDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (context.productionModule() == null) return null
        return object : UElementHandler() {
            override fun visitFile(node: UFile) {
                val file = node.sourcePsi as? KtFile ?: return
                val references = PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression::class.java)
                val reads = references.mapNotNull { reference ->
                    val target = reference.references.firstNotNullOfOrNull { it.resolve()?.navigationElement } as? KtProperty
                    target?.let { it to reference }
                }.groupBy({ it.first }, { it.second })
                val reported = mutableSetOf<Int>()
                fun report(expression: KtExpression) {
                    if (reported.add(expression.textOffset)) context.report(
                        ISSUE, expression, context.getLocation(expression),
                        "Consume Either by returning, binding, folding, exhaustive handling, or productive use",
                    )
                }
                file.accept(object : KtTreeVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        super.visitCallExpression(expression)
                        if (isEither(expression) && discarded(expression)) report(expression)
                    }
                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        val initializer = property.initializer ?: return
                        val subject = property.parent as? KtWhenExpression
                        if (subject?.subjectVariable == property) return
                        if (property.isLocal && isEither(initializer) &&
                            reads[property].orEmpty().none { !discarded(it) }) report(initializer)
                    }
                })
            }
        }
    }

    private fun isEither(expression: KtExpression): Boolean = analyze(expression) {
        (expression.expressionType as? KaClassType)?.classId?.asFqNameString() in
            setOf("arrow.core.Either", "arrow.core.Either.Left", "arrow.core.Either.Right")
    }

    private fun unitLambda(lambda: KtLambdaExpression): Boolean = analyze(lambda) {
        val type = lambda.expressionType as? KaFunctionType ?: return@analyze false
        (type.returnType as? KaClassType)?.classId?.asFqNameString() == "kotlin.Unit"
    }

    private fun discarded(expression: KtExpression): Boolean {
        var current = expression
        while (true) {
            when (val parent = current.parent) {
                is KtParenthesizedExpression -> current = parent
                is KtQualifiedExpression -> {
                    val selector = parent.selectorExpression as? KtCallExpression
                    val consumer = selector?.toUElementOfType<UCallExpression>()?.resolve()?.qualifiedSymbolName()
                    if (consumer == "arrow.core.Either.fold" ||
                        consumer?.startsWith("arrow.core.") == true && consumer.substringAfterLast('.') == "bind") return false
                    current = parent
                }
                is KtBlockExpression -> {
                    if (parent.statements.lastOrNull() != current) return true
                    val owner = parent.parent
                    if (owner is KtFunctionLiteral) return unitLambda(owner.parent as KtLambdaExpression)
                    if (owner is KtNamedFunction) return true
                    current = parent
                }
                is KtWhenEntry -> current = parent.parent as? KtWhenExpression ?: return false
                is KtIfExpression -> current = parent
                is KtContainerNodeForControlStructureBody -> current = parent.parent as? KtExpression ?: return false
                else -> return false
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "UnhandledEither", briefDescription = "Discarded typed failure",
            explanation = "Production code must consume Arrow Either. This type-resolved check rejects discarded calls, Unit-coerced lambda results and unread or statement-only local aliases. It deliberately does not prove all control-flow paths or effects inside external consumers.",
            category = Category.CORRECTNESS, priority = 8, severity = Severity.ERROR,
            implementation = Implementation(UnhandledEitherDetector::class.java, Scope.JAVA_FILE_SCOPE),
            suppressAnnotations = emptyList(),
        ).setAndroidSpecific(false)
    }
}
