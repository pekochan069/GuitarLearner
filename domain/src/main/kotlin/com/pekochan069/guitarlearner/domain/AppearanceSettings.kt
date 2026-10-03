package com.pekochan069.guitarlearner.domain

import arrow.core.Either
import kotlinx.coroutines.flow.StateFlow

enum class ThemePreference { System, Light, Dark }
enum class LanguagePreference { System, Korean, English }

data class AppearanceSnapshot(
    val theme: ThemePreference,
    val language: LanguagePreference?,
)

sealed interface AppearanceChange {
    data class Theme(val value: ThemePreference) : AppearanceChange
    data class Language(val value: LanguagePreference) : AppearanceChange
}

sealed interface AppearanceFailure {
    data object ReadFailed : AppearanceFailure
    data object WriteFailed : AppearanceFailure
    data object ThemeApplyFailed : AppearanceFailure
    data object LanguageApplyFailed : AppearanceFailure
}

interface AppearanceSettings {
    val current: StateFlow<AppearanceSnapshot>
    suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit>
}
