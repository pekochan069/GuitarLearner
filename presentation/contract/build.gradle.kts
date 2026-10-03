plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pekochan069.guitarlearner.presentation.contract"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

kotlin.compilerOptions.allWarningsAsErrors.set(true)

dependencies {
    implementation(libs.circuit.runtime)
    implementation(libs.kotlin.parcelize.runtime)
    lintChecks(project(":architecture-lint"))
    // Kotlin 2.2.10's Parcelize Gradle plugin does not attach to AGP's built-in Kotlin compilation.
    add("kotlinCompilerPluginClasspath", "org.jetbrains.kotlin:kotlin-parcelize-compiler:${libs.versions.kotlin.get()}")
}
