import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.metro) apply false
}

abstract class VerifyModuleBoundaries : DefaultTask() {
    @get:Input
    abstract val declared: ListProperty<String>

    @get:Input
    abstract val resolved: ListProperty<String>

    @TaskAction
    fun verify(): Unit {
        val edges = mapOf(
            ":domain" to emptySet(),
            ":presentation:contract" to emptySet(),
            ":presentation:logic" to setOf(":domain", ":presentation:contract"),
            ":ui" to setOf(":presentation:contract"),
            ":adapters" to setOf(":domain"),
            ":app" to setOf(":domain", ":presentation:contract", ":presentation:logic", ":ui", ":adapters"),
        )
        val failures = mutableListOf<String>()
        val actual = edges.keys.associateWith { mutableSetOf<String>() }
        declared.get().forEach { entry ->
            val (owner, configuration, dependency) = entry.split('|', limit = 3)
            if (dependency.startsWith(":")) {
                actual.getValue(owner) += dependency
                if (configuration != "implementation" || dependency !in edges.getValue(owner)) {
                    failures += "$owner declares $configuration($dependency); permitted project edges use implementation."
                }
            } else if (!allowedExternal(owner, dependency)) {
                failures += "$owner declares unsupported library $dependency."
            }
        }
        edges.forEach { (owner, expected) ->
            if (actual.getValue(owner) != expected) failures += "$owner has project edges ${actual.getValue(owner)}, expected $expected."
        }
        val resolvedOwners = mutableSetOf<String>()
        resolved.get().forEach { entry ->
            val (owner, configuration, dependency) = entry.split('|', limit = 3)
            resolvedOwners += owner
            if (dependency.startsWith(":")) {
                if (dependency != owner && dependency !in edges.getValue(owner)) {
                    failures += "$owner resolves forbidden project $dependency through $configuration."
                }
            } else if (!allowedExternal(owner, dependency, transitive = true)) {
                failures += "$owner resolves unsupported library $dependency through $configuration."
            }
        }
        if (resolvedOwners != edges.keys) failures += "Resolved production graphs cover $resolvedOwners, expected ${edges.keys}."
        if (failures.isNotEmpty()) throw GradleException(failures.distinct().joinToString("\n"))
        logger.lifecycle("Verified six-module project edges and resolved production classpaths.")
    }

    private fun allowedExternal(owner: String, dependency: String, transitive: Boolean = false): Boolean {
        if (owner == ":app" || owner == ":adapters") return true
        val group = dependency.substringBefore(':')
        val module = dependency.substringAfter(':')
        if (group == "org.jetbrains" && module == "annotations") return true
        if (group == "org.jetbrains.kotlin" && module in setOf("kotlin-stdlib", "kotlin-stdlib-common", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8")) return true
        if (group == "org.jetbrains.kotlin" && module == "kotlin-bom") return transitive
        if (group == "org.jetbrains.kotlinx" && module in setOf("kotlinx-coroutines-bom", "kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm", "kotlinx-coroutines-android")) {
            return if (module == "kotlinx-coroutines-android") transitive && owner != ":domain"
            else transitive || owner == ":domain" || owner == ":presentation:logic"
        }
        if (group == "io.arrow-kt" && module.removeSuffix("-jvm").removeSuffix("-android") in setOf("arrow-core", "arrow-atomic", "arrow-annotations")) {
            return owner == ":domain" || owner == ":presentation:logic"
        }
        if (owner == ":domain") return false
        if (group == "androidx.compose" && module == "compose-bom") return owner == ":ui" || owner == ":presentation:logic"
        if (group == "org.jetbrains.kotlin" && module in setOf("kotlin-parcelize-runtime", "kotlin-android-extensions-runtime")) {
            return owner == ":presentation:contract" || transitive && owner in setOf(":ui", ":presentation:logic")
        }
        if (group == "com.slack.circuit") {
            val common = module.removeSuffix("-android").removeSuffix("-jvm")
            return common in when (owner) {
                ":presentation:contract" -> setOf("circuit-runtime", "circuit-runtime-screen")
                ":presentation:logic" -> setOf("circuit-runtime", "circuit-runtime-screen", "circuit-runtime-presenter")
                ":ui" -> setOf("circuit-runtime", "circuit-runtime-screen", "circuit-runtime-ui")
                else -> emptySet()
            }
        }
        if (group == "androidx.compose.runtime") return transitive || owner == ":presentation:logic" || owner == ":ui"
        if (transitive && dependency in setOf(
                "org.jetbrains.compose.runtime:runtime", "org.jetbrains.compose.annotation-internal:annotation",
                "org.jetbrains.compose.collection-internal:collection", "org.jspecify:jspecify",
                "org.jetbrains.kotlinx:kotlinx-collections-immutable", "org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm",
            )) return true
        if (group == "androidx.annotation" && module in setOf("annotation", "annotation-jvm", "annotation-experimental")) return true
        if (group == "androidx.collection" && module in setOf("collection", "collection-jvm", "collection-ktx")) return true
        if (owner == ":ui") {
            if (group == "androidx.compose.material") return transitive && module in setOf("material-ripple", "material-ripple-android")
            if (group.startsWith("androidx.compose.")) return true
        }
        if (!transitive) return false
        if (owner == ":ui" || owner == ":presentation:logic") {
            if (dependency in setOf(
                    "androidx.lifecycle:lifecycle-common", "androidx.lifecycle:lifecycle-common-jvm",
                    "androidx.lifecycle:lifecycle-runtime", "androidx.lifecycle:lifecycle-runtime-android",
                    "androidx.lifecycle:lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx-android",
                    "androidx.lifecycle:lifecycle-runtime-compose", "androidx.lifecycle:lifecycle-runtime-compose-android",
                    "androidx.savedstate:savedstate", "androidx.savedstate:savedstate-android",
                    "androidx.savedstate:savedstate-compose", "androidx.savedstate:savedstate-compose-android",
                    "androidx.core:core", "androidx.core:core-ktx", "androidx.core:core-viewtree",
                    "androidx.arch.core:core-common", "androidx.arch.core:core-runtime",
                    "androidx.concurrent:concurrent-futures", "androidx.interpolator:interpolator",
                    "androidx.versionedparcelable:versionedparcelable", "androidx.startup:startup-runtime",
                    "androidx.profileinstaller:profileinstaller", "androidx.tracing:tracing",
                    "com.google.guava:listenablefuture", "org.jetbrains.kotlinx:kotlinx-serialization-bom",
                    "org.jetbrains.kotlinx:kotlinx-serialization-core", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm",
                )) return true
        }
        if (owner == ":ui") {
            return dependency in setOf(
                "androidx.core:core", "androidx.core:core-ktx", "androidx.core:core-viewtree",
                "androidx.lifecycle:lifecycle-common", "androidx.lifecycle:lifecycle-common-jvm",
                "androidx.lifecycle:lifecycle-runtime", "androidx.lifecycle:lifecycle-runtime-android",
                "androidx.lifecycle:lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx-android",
                "androidx.lifecycle:lifecycle-viewmodel", "androidx.lifecycle:lifecycle-viewmodel-android",
                "androidx.lifecycle:lifecycle-viewmodel-ktx", "androidx.lifecycle:lifecycle-viewmodel-compose",
                "androidx.lifecycle:lifecycle-viewmodel-compose-android", "androidx.lifecycle:lifecycle-viewmodel-savedstate",
                "androidx.lifecycle:lifecycle-livedata-core", "androidx.lifecycle:lifecycle-livedata-core-ktx",
                "androidx.lifecycle:lifecycle-runtime-compose", "androidx.lifecycle:lifecycle-runtime-compose-android",
                "androidx.savedstate:savedstate", "androidx.savedstate:savedstate-android", "androidx.savedstate:savedstate-ktx",
                "androidx.activity:activity", "androidx.activity:activity-ktx", "androidx.activity:activity-compose",
                "androidx.customview:customview", "androidx.customview:customview-poolingcontainer",
                "androidx.tracing:tracing", "androidx.tracing:tracing-ktx",
                "androidx.emoji2:emoji2", "androidx.emoji2:emoji2-views-helper",
                "androidx.startup:startup-runtime", "androidx.profileinstaller:profileinstaller",
                "androidx.arch.core:core-common", "androidx.arch.core:core-runtime",
                "androidx.interpolator:interpolator", "androidx.resourceinspection:resourceinspection-annotation",
                "androidx.graphics:graphics-path", "androidx.graphics:graphics-shapes",
                "androidx.graphics:graphics-shapes-android", "androidx.autofill:autofill",
                "androidx.documentfile:documentfile", "androidx.dynamicanimation:dynamicanimation",
                "androidx.legacy:legacy-support-core-utils", "androidx.lifecycle:lifecycle-common-java8",
                "androidx.lifecycle:lifecycle-livedata", "androidx.lifecycle:lifecycle-process",
                "androidx.lifecycle:lifecycle-viewmodel-savedstate-android", "androidx.loader:loader",
                "androidx.localbroadcastmanager:localbroadcastmanager", "androidx.print:print",
                "androidx.transition:transition", "androidx.window:window", "androidx.window:window-core",
                "androidx.window:window-core-android", "org.jetbrains.androidx.lifecycle:lifecycle-common",
                "org.jetbrains.androidx.lifecycle:lifecycle-runtime", "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose",
                "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", "org.jetbrains.androidx.savedstate:savedstate",
                "org.jetbrains.androidx.savedstate:savedstate-compose", "org.jetbrains.compose.runtime:runtime-saveable",
                "org.jetbrains.compose.ui:ui", "org.jetbrains.compose.ui:ui-geometry", "org.jetbrains.compose.ui:ui-graphics",
                "org.jetbrains.compose.ui:ui-text", "org.jetbrains.compose.ui:ui-unit", "org.jetbrains.compose.ui:ui-util",
            )
        }
        return false
    }

    companion object {
        fun encodeComponents(owner: String, configuration: String, root: ResolvedComponentResult): List<String> {
            val pending = ArrayDeque<ResolvedComponentResult>()
            val visited = mutableSetOf<org.gradle.api.artifacts.component.ComponentIdentifier>()
            val result = mutableListOf<String>()
            pending += root
            while (pending.isNotEmpty()) {
                val component = pending.removeFirst()
                if (!visited.add(component.id)) continue
                val id = component.id
                val name = when (id) {
                    is ProjectComponentIdentifier -> id.projectPath
                    is ModuleComponentIdentifier -> "${id.group}:${id.module}"
                    else -> throw GradleException("Unsupported component $id in $owner/$configuration")
                }
                result += "$owner|$configuration|$name"
                component.dependencies.forEach { dependency ->
                    if (dependency !is ResolvedDependencyResult) {
                        throw GradleException("Unresolved production dependency ${dependency.requested} in $owner/$configuration")
                    }
                    pending += dependency.selected
                }
            }
            return result
        }
    }
}

val verifyModuleBoundaries = tasks.register<VerifyModuleBoundaries>("verifyModuleBoundaries") {
    group = "verification"
    description = "Check declared module edges and resolved production dependencies."
    declared.convention(emptyList())
    resolved.convention(emptyList())
}

gradle.projectsEvaluated {
    subprojects.filter { it.path in setOf(":domain", ":presentation:contract", ":presentation:logic", ":ui", ":adapters", ":app") }.forEach { module ->
        val modulePath = module.path
        val productionClasspaths = module.configurations.filter {
            it.isCanBeResolved &&
                (it.name.endsWith("compileClasspath", ignoreCase = true) || it.name.endsWith("runtimeClasspath", ignoreCase = true)) &&
                !it.name.contains("test", ignoreCase = true)
        }
        if (productionClasspaths.isEmpty()) throw GradleException("No production classpaths found for $modulePath")
        val productionDeclarations = productionClasspaths.flatMap { it.hierarchy }.distinct().flatMap { configuration ->
            configuration.dependencies.map { dependency ->
                val name = when (dependency) {
                    is ProjectDependency -> dependency.path
                    is org.gradle.api.artifacts.ExternalModuleDependency -> "${dependency.group}:${dependency.name}"
                    else -> throw GradleException("Unsupported declared production dependency $dependency in $modulePath/${configuration.name}")
                }
                "$modulePath|${configuration.name}|$name"
            }
        }
        verifyModuleBoundaries.configure { declared.addAll(productionDeclarations) }
        productionClasspaths.forEach { configuration ->
            val configurationName = configuration.name
            val components = configuration.incoming.resolutionResult.rootComponent.map { component ->
                VerifyModuleBoundaries.encodeComponents(modulePath, configurationName, component)
            }
            verifyModuleBoundaries.configure { resolved.addAll(components) }
        }
        module.tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyModuleBoundaries) }
    }
}
