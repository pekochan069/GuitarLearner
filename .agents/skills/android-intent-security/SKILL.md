---
name: android-intent-security
description: Audit or harden Android manifests and Intent-handling code against exported-component abuse, Intent redirection, unsafe PendingIntents, untrusted broadcasts, provider exposure, and Binder caller confusion.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-06-25'
  keywords: [Android, security, Intent, PendingIntent, ContentProvider, Binder, exported]
---

# Android Intent Security

This skill covers local Android component and inter-app communication security, not network or server security.

For audit, explanation, or plan requests, inspect and report only. For explicit hardening requests, make requested local changes and run non-destructive validation. Confirm before dependency additions, manifest contract changes affecting partner apps, key/certificate changes, external writes, or material scope expansion.

## 1. Map trust boundaries

Inspect `AndroidManifest.xml` plus every caller/handler for affected Activities, Services, Receivers, Providers, `PendingIntent`s, nested Intents, URI grants, and Binder transactions. Record:

- exported status and intent filters;
- required permissions and protection levels;
- accepted callers/senders and how identity is established;
- accepted actions, data, categories, extras, flags, and target components;
- `onCreate` and `onNewIntent` paths;
- installed AndroidX Core and minimum/target SDK from Gradle.

Mapping is complete when every externally reachable path has an explicit trust decision and code/manifest evidence.

## 2. Route

| Boundary | Reference |
| --- | --- |
| nested Intent, forwarding, URI grants, `onNewIntent` | [Intent handling](references/intent-handling.md) |
| `PendingIntent`, notifications, replies, alarms | [PendingIntent](references/pending-intents.md) |
| exported components, permissions, broadcasts, providers | [component exposure](references/component-exposure.md) |
| bound services, Binder UID/package/signature checks | [Binder callers](references/binder-callers.md) |

Load only affected boundaries. Verify API availability against project versions rather than assuming a minimum SDK or dependency.

## 3. Findings and fixes

Every finding includes:

- `file:line` and reachable entry point;
- attacker-controlled input and required preconditions;
- privilege/data impact;
- existing control and why it fails;
- smallest remediation preserving intended integrations;
- compile, test, and adversarial validation.

For changes, apply validation at every delivery path, including reused Activity instances. Prefer declarative manifest/permission boundaries and explicit targets over repeated runtime checks. Security logs contain enough diagnostic context for developers without leaking secrets to users.

## Response

Lead with highest-severity findings. For an implementation, add changed paths and exact validation results; include a diff only when requested.

```markdown
### <severity> — <finding>
- **Location:** `path:line`
- **Reachability:** <entry point and attacker control>
- **Impact:** <privilege or data consequence>
- **Evidence:** <manifest/code fact>
- **Fix:** <exact boundary or code change>
- **Validation:** <performed or required checks>
```

Complete when every in-scope external entry point is accounted for, findings have exploit conditions and remediation, changed targets compile/test when implemented, and unresolved partner/API assumptions are explicit.
