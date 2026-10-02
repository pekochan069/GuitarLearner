# System-Bar Legibility

`ComponentActivity.enableEdgeToEdge()` normally configures icon appearance from supplied/default styles. When managing bars through `WindowCompat`, set light-status/navigation appearance from the actual background luminance or theme and update it with theme changes.

```kotlin
SideEffect {
    val window = (view.context as? Activity)?.window ?: return@SideEffect
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
```

On API 29+, navigation-bar contrast enforcement may add a three-button-navigation scrim. Disable it only when the app supplies sufficient contrast and the bottom surface intentionally extends behind the bar:

```kotlin
if (Build.VERSION.SDK_INT >= 29) {
    window.isNavigationBarContrastEnforced = false
}
```

Use a protection gradient or surface only when content behind transparent bars makes icons illegible. Size protection from `WindowInsets.statusBars`/`navigationBars`; keep it decorative and non-interactive.

Verify light/dark theme, gesture/three-button navigation, transparent and busy backgrounds, and runtime theme changes.
