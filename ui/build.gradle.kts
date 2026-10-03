plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pekochan069.guitarlearner.ui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

kotlin.compilerOptions.allWarningsAsErrors.set(true)

dependencies {
    implementation(project(":presentation:contract"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.circuit.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    lintChecks(project(":architecture-lint"))
}
