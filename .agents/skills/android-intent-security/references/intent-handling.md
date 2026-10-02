# Intent Handling

Treat actions, data, categories, extras, flags, nested Intents, and URI grants from exported entry points as untrusted.

## Nested Intent / redirection

Prefer reconstructing a new explicit Intent from individually validated values. When forwarding is required:

- allowlist exact destination components and permitted actions/data/types/extras;
- reject or strip URI grant flags unless the flow explicitly grants a validated URI;
- make the destination explicit after validation;
- use installed `IntentSanitizer` only when its version/API fits the project; configure a narrow allowlist and choose throwing versus filtering deliberately;
- never treat “same package” alone as authorization to reach a private privileged component.

A fallback without `IntentSanitizer` performs the same explicit allowlist checks with platform APIs. Resolving a target or checking `exported` is not a substitute for authorizing the requested operation.

## Lifecycle

Apply the same validation and authorization in `onCreate` and `onNewIntent`. Update the Activity's current Intent only after deciding how the application expects `intent` to behave; do not use `setIntent` as a security control.

Validate extra presence, type, size, enum/range, URI scheme/authority/path, and caller-bound identifiers before use. Error responses reveal no internal component names, query text, tokens, or secrets.

## Validation

Test direct launch, warm `singleTop` delivery, missing/wrong-type extras, disallowed target/action/data, URI-grant flags, and a legitimate integration path. Confirm rejected input causes no privileged side effect.
