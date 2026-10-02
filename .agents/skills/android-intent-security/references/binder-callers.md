# Binder Caller Verification

Manifest permissions are the preferred coarse boundary. For per-caller service authorization, enforce identity inside every sensitive Binder transaction, not only `onBind`, because bindings and Binder objects may be reused.

- capture `Binder.getCallingUid()` before clearing calling identity;
- allow same-UID calls only when that matches the trust model;
- resolve all packages for the UID rather than trusting the first package;
- verify signing identity with APIs available at the project's SDK levels, including certificate rotation requirements;
- compare certificate bytes/digests in a canonical format and keep trusted values out of user-controlled storage;
- reject unknown, shared, or mismatched UIDs before privileged work.

Package name alone is not identity. Handle removed/reinstalled packages and key rotation according to the partner contract.

Test same-app, trusted partner, untrusted app, shared UID/package set, rotated certificate when supported, and repeated calls on an existing binding.
