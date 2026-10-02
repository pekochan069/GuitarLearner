# PendingIntent Security

Use `FLAG_IMMUTABLE` by default. Mutability is justified only when the recipient must fill supported fields, such as direct reply.

For a mutable `PendingIntent`:

- set an explicit component on the base Intent; treat any unavoidable platform-specific exception as a separate high-risk design requiring documented threat analysis and adversarial validation;
- constrain action, data, categories, and extras to the required flow;
- use a request code and update/cancel behavior that does not accidentally alias another capability;
- avoid placing reusable secrets or broader authority in extras;
- validate any recipient-supplied fields at the receiving component.

An implicit mutable `PendingIntent` is a capability-hijacking risk. Immutability does not repair an over-privileged or wrongly targeted base Intent.

Inspect Activity, Service, Broadcast, notification, alarm, widget, and shortcut creation sites. Test intended delivery, replay/update behavior, recipient-controlled fields, and rejection of unexpected data.
