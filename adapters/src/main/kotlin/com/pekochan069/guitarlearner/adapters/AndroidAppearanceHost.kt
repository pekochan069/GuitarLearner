package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.ThemePreference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAppearanceHost(
    private val preferences: SharedPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val readLanguage: () -> LanguagePreference? = ::readNativeLanguage,
    private val applyTheme: (ThemePreference) -> Unit = ::applyNativeTheme,
    private val applyLanguage: (LanguagePreference) -> Unit = ::applyNativeLanguage,
) : AppearanceSettings {
    private val initialTheme: Either<AppearanceFailure, ThemePreference> = readStoredTheme()
    private var initialFailure: AppearanceFailure? = initialTheme.fold({ it }, { null })
    private val snapshot = MutableStateFlow(
        AppearanceSnapshot(initialTheme.getOrNull() ?: ThemePreference.System, LanguagePreference.System),
    )
    private val mutations = Mutex()
    override val current: StateFlow<AppearanceSnapshot> = snapshot.asStateFlow()

    fun prepareActivityTheme(): Either<AppearanceFailure, Unit> = either {
        applyThemeChecked(snapshot.value.theme).bind()
        initialFailure?.let { raise(it) }
    }

    fun refreshPlatformLanguage(): Either<AppearanceFailure, Unit> = either {
        val language = readLanguageChecked().bind()
        snapshot.update { it.copy(language = language) }
    }

    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> {
        val caller = currentCoroutineContext()
        val result: Either<AppearanceFailure, Unit> = mutations.withLock {
            caller.ensureActive()
            when (change) {
                is AppearanceChange.Theme -> {
                    // A checked commit must finish publication/application even if it recreates the caller.
                    withContext(NonCancellable) {
                        either<AppearanceFailure, Unit> {
                            if (snapshot.value.theme != change.value || initialFailure != null) {
                                val committed = withContext(io) { commitTheme(change.value) }.bind()
                                ensure(committed) { AppearanceFailure.WriteFailed }
                                withContext(main) {
                                    initialFailure = null
                                    snapshot.update { it.copy(theme = change.value) }
                                }
                            }
                            withContext(main) { applyThemeChecked(change.value).bind() }
                        }
                    }
                }
                is AppearanceChange.Language -> {
                    withContext(NonCancellable + main) {
                        either<AppearanceFailure, Unit> {
                            val application = applyLanguageChecked(change.value)
                            val observation = refreshPlatformLanguage()
                            application.bind()
                            observation.bind()
                        }
                    }
                }
            }
        }
        caller.ensureActive()
        return result
    }

    private fun readStoredTheme(): Either<AppearanceFailure, ThemePreference> = try {
        val stored = preferences.getString("theme_mode", null)
        (ThemePreference.entries.firstOrNull { it.name == stored } ?: ThemePreference.System).right()
    } catch (_: ClassCastException) {
        AppearanceFailure.ReadFailed.left()
    } catch (_: SecurityException) {
        AppearanceFailure.ReadFailed.left()
    }

    private fun commitTheme(theme: ThemePreference): Either<AppearanceFailure, Boolean> = try {
        val editor = preferences.edit()
        editor.putString("theme_mode", theme.name)
        val committed = editor.commit()
        committed.right()
    } catch (_: SecurityException) {
        AppearanceFailure.WriteFailed.left()
    }

    private fun applyThemeChecked(theme: ThemePreference): Either<AppearanceFailure, Unit> = try {
        applyTheme(theme)
        Unit.right()
    } catch (_: SecurityException) {
        AppearanceFailure.ThemeApplyFailed.left()
    }

    private fun applyLanguageChecked(language: LanguagePreference): Either<AppearanceFailure, Unit> = try {
        applyLanguage(language)
        Unit.right()
    } catch (_: SecurityException) {
        AppearanceFailure.LanguageApplyFailed.left()
    }

    private fun readLanguageChecked(): Either<AppearanceFailure, LanguagePreference?> = try {
        readLanguage().right()
    } catch (_: SecurityException) {
        AppearanceFailure.LanguageApplyFailed.left()
    }
}

private fun applyNativeTheme(theme: ThemePreference): Unit {
    AppCompatDelegate.setDefaultNightMode(
        when (theme) {
            ThemePreference.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        },
    )
}

private fun applyNativeLanguage(language: LanguagePreference): Unit {
    val tag = when (language) {
        LanguagePreference.System -> ""
        LanguagePreference.Korean -> "ko"
        LanguagePreference.English -> "en"
    }
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

private fun readNativeLanguage(): LanguagePreference? =
    when (AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()) {
        "" -> LanguagePreference.System
        "ko" -> LanguagePreference.Korean
        "en" -> LanguagePreference.English
        else -> null
    }
