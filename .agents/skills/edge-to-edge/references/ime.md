# IME Insets

`WindowInsets.ime`, `imePadding()`, or `WindowInsetsRulers.Ime` handles software-keyboard avoidance. `WindowInsets.safeDrawing` protects system bars/cutouts, not the IME.

Verify the affected Activity uses resize behavior compatible with edge-to-edge, commonly `android:windowSoftInputMode="adjustResize"`. Avoid deprecated `SOFT_INPUT_ADJUST_RESIZE` mutation.

## Ruler strategy

Use when constraining nested content without introducing padding ownership:

```kotlin
Scaffold { innerPadding ->
    Column(
        Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .fitInside(WindowInsetsRulers.Ime.current)
            .verticalScroll(rememberScrollState()),
    ) { /* fields */ }
}
```

## Padding strategy

Use when no ancestor already owns IME insets:

```kotlin
Scaffold { innerPadding ->
    Column(
        Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) { /* fields */ }
}
```

Place `imePadding()` before scrolling so the viewport shrinks and the focused field can scroll into view. Avoid combining it with ancestor-provided IME padding. `safeDrawingPadding()`, `safeContentPadding()`, and `safeGesturesPadding()` alone do not prove IME protection.

Verify keyboard closed/open, focus near top/bottom, focus transfer, back dismissal, and orientation changes. Confirm the focused field stays visible without excess bottom space.
