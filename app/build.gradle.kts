plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.pekochan069.guitarlearner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pekochan069.guitarlearner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        informational += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

kotlin.compilerOptions.allWarningsAsErrors.set(true)

dependencies {
    implementation(project(":domain"))
    implementation(project(":presentation:contract"))
    implementation(project(":presentation:logic"))
    implementation(project(":ui"))
    implementation(project(":adapters"))
    implementation(libs.arrow.core)
    implementation(libs.circuit.foundation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.material3)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    lintChecks(project(":architecture-lint"))
}
