# Saved project progress

Updated: 2026-10-09 UTC.

## Decisions

- Build from scratch in `niksubscriptions23-maker/DigAddFix`.
- Brave stable first; revisit a built-in browser later.
- Browser-only family DNS, separate app blocking, hardcoded starter lists until final lists arrive.
- Full-screen block overlay for apps and detected current websites; remove after five seconds while the policy remains active.
- Keyword list reserved; Brave page-text/media scanning is deferred.

## Implemented v0.1

Native setup/status UI, narrow Brave-only DNS VPN with IPv4/IPv6 UDP and TCP DNS handling, family DNS-over-HTTPS, local domain blocks, supported Google SafeSearch mappings, separate accessibility app interruption, current-address website correlation, five-second overlay, bundled starter lists, core regression suite, and Android CI workflow.

No history database, telemetry, account system, certificate interception, or general webpage-content reading.

## Verification

- Local JVM suite: 61 checks passed, including 10,000 malformed DNS/IP fixtures.
- Android CI passed: `:core:check`, `:app:assembleDebug`, and `:app:lintDebug` for source commit `6e565260463d901940a638c0dba45dac2bc4fb88`. [Successful workflow run](https://github.com/niksubscriptions23-maker/DigAddFix/actions/runs/37892257903).
- Lint: zero errors, 11 warnings about SDK currency, backup declarations, and untranslated UI strings. The manifest has one narrowly documented foreground-service permission suppression because lint does not model active VPN eligibility; no unrelated alarm permission was added.
- Local Android build unavailable: Android SDK is absent and Gradle's distribution host is unreachable from this workspace. CI provided the compile/lint evidence.
- Debug APK produced and downloaded: `DigAddFix-0.1.0-debug.apk`, 47,452 bytes, SHA-256 `bcf238df8057b4eefa7a2a7a99d2ca744625215a04d8fef7dc6f653949e9d6ed`. APK manifest, DEX, and bundled rule assets checked. This is a test build, not a production-signed release.
- Device verification: not run. See `DEVICE_VERIFICATION.md` for isolation, SafeSearch, overlay timing, browser modes, and lifecycle checks.

## Next work

1. Verify on an Android device with Brave stable, especially system-DNS routing, full-screen coverage, and URL accessibility.
2. Replace starter lists with the user's final lists.
3. Evaluate broader browser enforcement and the built-in browser before promising page-level keyword/media blocking.
4. Address remaining UI localization/SDK/backup warnings and configure stable production signing before a production release.

## Publishing checkpoint

Source and build fixes are published on `main` in the active repository. The SDK setup action's obsolete default `tools` package was replaced with `platform-tools`. Full-page keyword scanning and device verification remain explicitly pending. This progress-only checkpoint does not change the APK source.
