# Full-Screen Dialogs

A Compose dialog needs edge-to-edge window behavior when it intentionally fills the platform window, commonly with `usePlatformDefaultWidth = false` plus full-size content.

```kotlin
Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
) {
    FullScreenContent()
}
```

The dialog content still owns safe placement for interactive/readable elements. Apply system-bar and IME handling inside the dialog using the same single-owner rules as an Activity. Verify dismissal, focus, keyboard open/closed, system-bar icon contrast, and supported orientations.
