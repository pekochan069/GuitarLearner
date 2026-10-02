# System Insets

## Strategy

Choose one owner at each layout boundary:

1. Material component or `Scaffold` inset handling.
2. `PaddingValues` passed to content.
3. `windowInsetsPadding` / `safeDrawingPadding` outside scaffolds.
4. `WindowInsetsRulers` for nested constraints where padding is unsuitable.

`safeDrawing` covers persistent drawing obstructions such as system bars and cutouts; it does not generally establish IME avoidance.

## Scaffold and lists

```kotlin
Scaffold { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding),
        contentPadding = innerPadding,
    ) { /* items */ }
}
```

Use `contentPadding` for lazy content rather than padding its parent, so items can scroll behind bars while endpoints remain safe. A FAB outside inset-aware `Scaffold` needs safe bottom/side placement.

Material top/bottom app bars, navigation bars/rails, sheets, and drawers may own their relevant insets. Pass component `windowInsets` when customization is required; parent padding can prevent backgrounds from drawing edge to edge.

Adaptive scaffolds can protect their navigation component without propagating safe content bounds to each destination. Apply destination/list/FAB insets inside individual panes; padding the whole adaptive scaffold clips the edge-to-edge surface.

For a decorative system-bar-sized region, use `windowInsetsTopHeight` or the matching inset-size modifier. Keep interactive content separately protected.

## Migration scope

Android target-SDK behavior and `enableEdgeToEdge()` requirements depend on current SDK, Activity base class, and window configuration. Inspect them before editing. Apply an all-Activity migration only when requested; otherwise repair the affected flow.
