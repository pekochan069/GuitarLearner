# Component Exposure

## Components

Set `android:exported="false"` when external launch is unnecessary. For required exposure, narrow the contract with exact intent filters, signature permissions for same-signer ecosystems, and runtime authorization for caller- or resource-specific decisions.

An exported component remains untrusted even when work happens off the main thread. Internal validation must cover every privileged action.

## Broadcasts

Use protected system broadcasts only for documented system actions. Protect app-defined broadcasts with signature permissions or register dynamic receivers as not exported when external senders are unnecessary. `Binder.getCallingUid()` inside `BroadcastReceiver.onReceive` does not identify the broadcast sender.

Avoid sticky broadcasts for sensitive or application state. Prefer in-process observable state for internal communication.

## Providers

Internal providers use `android:exported="false"`. Exported providers define least-privilege read/write permissions and deliberate URI-grant behavior. Validate URI paths, projections, selections, sort order, and write values. Use parameterized selections and strict projection maps where applicable; gate platform query-builder APIs by actual SDK availability.

## Validation

Enumerate exported components from the merged manifest. Exercise unauthorized and authorized launches/sends/queries, permission denial, malformed inputs, and temporary URI grants. Preserve documented partner contracts.
