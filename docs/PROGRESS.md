# Saved project progress

Updated: 2026-10-09 UTC.

## Decisions

- Build from scratch in `niksubscriptions23-maker/DigAddFix`.
- Supported browsers are now stable Brave, Chrome and Firefox equally; revisit a built-in browser later. The user's Brave preference did not authorize limiting browser coverage to Brave.
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

1. Verify on an Android device with each installed stable Brave, Chrome and Firefox, especially system-DNS routing, full-screen coverage, URL accessibility, and browser installation changes.
2. Replace starter lists with the user's final lists.
3. Evaluate broader browser enforcement and the built-in browser before promising page-level keyword/media blocking.
4. Address remaining UI localization/SDK/backup warnings and configure stable production signing before a production release.

## Publishing checkpoint

Source and build fixes are published on `main` in the active repository. The SDK setup action's obsolete default `tools` package was replaced with `platform-tools`. Full-page keyword scanning and device verification remain explicitly pending. This progress-only checkpoint does not change the APK source.

## Current v0.2 change

The user explicitly broadened browser coverage to Chrome and Firefox and requested other known browsers on the app blocklist.

- Shared exact supported-package registry and address-node allowlist for stable Brave, Chrome, and Firefox.
- VPN includes only installed/enabled supported packages; Chrome-only and Firefox-only configurations work without Brave. Empty scope stops activation before building the VPN.
- Active VPN scope rebuilds on browser package install/remove/replace/enable changes, including unchanged package-name sets whose UIDs may have changed. DNS replies are tied to their original VPN session during reconfiguration.
- Website overlay correlation is generalized to the foreground protected browser. The five-second overlay remains unchanged.
- Thirty other known browser/channel packages added to the app blocklist, with matching explicit manifest visibility. No new package-wide network routing or all-app inventory permission.
- Setup/status UI reports each browser's installed/active state and offers a supported-browser picker for opening links and diagnostics. Accessibility disclosure and instructions updated.
- Version code 2, version 0.2.0.

Local core suite: **127 checks passed**, including all eight supported-browser installation combinations, Chrome-only/Firefox-only/no-browser cases, contradictory rules, package-specific address IDs, other-browser rules, and 10,000 DNS/IP fixtures. Android v0.2 build/lint and APK are pending CI. Device checks are not run.

See `BROWSERS.md` for policy mapping and verified package references. Other search engines, custom encrypted DNS, browser extensions/proxies, unlisted browsers, and Brave/Chrome/Firefox page-text/media scanning remain coverage limits.
