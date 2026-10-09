# Project continuity

Read `docs/PROGRESS.md` and `docs/DEVICE_VERIFICATION.md` before continuing. Record meaningful changes, decisions, and actual verification results in the progress file and commit work each session.

- Active repository: `niksubscriptions23-maker/DigAddFix`. This fresh project supersedes `nikhil-more/DigAddFixV2`; do not publish there.
- Start with Brave stable. The built-in browser and full-page keyword/media scanning are deferred.
- Family DNS applies only to Brave browser DNS. Never add default VPN routes, change device-wide DNS, or fall back to an empty allowed-app list. Other apps and embedded webviews retain their own networking.
- App blocking is a separate foreground policy, including offline apps. Initial package/domain lists are bundled; the user's final lists are pending.
- A detected block shows an opaque full-screen overlay for five seconds, then removes it. Repeated notifications for the same block must not extend the timer. Blocking remains active afterward.
- Correlate website overlays with the current committed Brave address, not arbitrary background DNS resources. Read only URL-bar text; do not collect page bodies, messages, passwords, or browsing history.
- Show truthful permission and service status. This is voluntary protection, not an uninstall-resistant device-management solution.
- Run `python3 scripts/check_core.py` and the Android build/lint checks when relevant. Do not describe device checks as passed without device evidence.
