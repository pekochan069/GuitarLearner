# Compose Motion Plan Template

Each plan must stand alone for an executor with no conversation context.

```markdown
# NNN — <Imperative title>

- **Status**: TODO
- **Commit**: <git rev-parse --short HEAD>
- **Severity**: HIGH | MEDIUM | LOW
- **Category**: <audit category>
- **Source set**: <active source set derived from Gradle>
- **Estimated scope**: <files and rough size>

## Problem

State what is wrong, where, and why it harms feel, continuity, accessibility, or frame delivery. Cite every location and quote current Kotlin:

​```kotlin
// shared/src/commonMain/.../Lesson.kt:41 — current
if (showResult) ResultCard(result)
​```

## Target

Spell out exact end state, APIs, specs, state ownership, and source set:

​```kotlin
AnimatedVisibility(
    visible = showResult,
    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
        scaleIn(
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            initialScale = 0.95f,
        ),
    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
) {
    ResultCard(result)
}
​```

State reduced-motion behavior and whether Material already owns any motion.

## Repo conventions

- Compose Multiplatform version: <from version catalog>
- Material version: <from version catalog>
- Closest existing pattern: `<file:line>`
- Motion source: Material component | `MaterialTheme.motionScheme` | existing project spec | justified new spec
- Experimental API and required opt-in: <none or exact annotation>
- For `animateBounds`: stable `LookaheadScope` owner, coordinate space, and motion-frame behavior

## Steps

1. <One concrete edit: file, code, and resulting behavior.>
2. …

## Boundaries

- Keep shared behavior in `commonMain`.
- Keep platform APIs in matching active source sets.
- Preserve state ownership, semantics, focus, click targets, and navigation behavior.
- Keep correct Material component motion and indication.
- Add no dependency for built-in Compose capability.
- Touch no unrelated files.
- If cited code drifted or API is unavailable at installed version, stop and report rather than improvise.

## Verification

- **Mechanical**:
  - `./gradlew :shared:compileKotlinJvm`
  - `./gradlew :androidApp:compileDebugKotlin`
  - `<smallest relevant test task>`
- **Behavior**:
  - Trigger `<interaction>` at normal speed and confirm `<observable result>`.
  - Repeat rapidly; confirm motion retargets without restarting or blocking input.
  - Android: test animator duration scale `0x` and `10x` when affected.
  - Active non-Android targets: test their reduced-motion policy when affected.
  - Desktop: test keyboard and mouse path when affected.
  - Gesture: test physical device and confirm velocity, cancellation, and boundary resistance.
  - Performance: use Perfetto/System Trace only when plan addresses measured jank.
- **Done when**: <machine- and eye-checkable completion criteria>.
- **Evidence to retain**: exact command results, affected-target manual-check results, and remaining unverified behavior.
```

## Author checks

- One plan per finding unless files and fix pattern are identical.
- Values come from `AUDIT.md`, Material motion scheme, or cited repo convention.
- Verification uses only tasks present in this Gradle project.
- Feel check is concrete; performance plans name measurement evidence.
- Update `plans/README.md` with number, title, severity, status, order, and dependencies.
