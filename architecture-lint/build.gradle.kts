plugins { alias(libs.plugins.kotlin.jvm) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.lint.api)
    testImplementation(libs.junit)
}
