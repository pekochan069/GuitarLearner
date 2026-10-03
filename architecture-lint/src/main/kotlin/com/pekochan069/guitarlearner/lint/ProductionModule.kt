package com.pekochan069.guitarlearner.lint

import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal enum class ProductionModule { DOMAIN, CONTRACT, LOGIC, UI, ADAPTERS, APP }

internal fun JavaContext.productionModule(): ProductionModule? {
    if (isGeneratedSource || sourceSetType.name.contains("TEST")) return null
    val path = file.invariantSeparatorsPath
    if (listOf("/src/test/", "/src/androidTest/", "/src/testFixtures/", "/build/generated/").any(path::contains)) return null
    return when (project.dir.name) {
        "domain" -> ProductionModule.DOMAIN
        "contract" -> ProductionModule.CONTRACT
        "logic" -> ProductionModule.LOGIC
        "ui" -> ProductionModule.UI
        "adapters" -> ProductionModule.ADAPTERS
        "app" -> ProductionModule.APP
        else -> null
    }
}

internal fun PsiElement.qualifiedSymbolName(): String? {
    (navigationElement as? KtNamedDeclaration)?.fqName?.asString()?.let { return it }
    return when (this) {
        is PsiClass -> qualifiedName
        is PsiMember -> containingClass?.qualifiedName?.let { "$it.$name" }
        is KtNamedDeclaration -> fqName?.asString()
        else -> null
    }
}

internal fun PsiType.referencedClasses(): List<String> = when (this) {
    is PsiClassType -> listOfNotNull(resolve()?.qualifiedName) + parameters.flatMap { it.referencedClasses() }
    is PsiArrayType -> componentType.referencedClasses()
    is PsiWildcardType -> bound?.referencedClasses().orEmpty()
    else -> emptyList()
}
