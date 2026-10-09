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
- Local Android build unavailable: Android SDK is absent and Gradle's distribution host is unreachable from this workspace. GitHub Actions is configured to compile, lint, and publish a debug APK; its result is pending.
- Device verification: not run. See `DEVICE_VERIFICATION.md` for isolation, SafeSearch, overlay timing, browser modes, and lifecycle checks.

## Next work

1. Publish the fresh project to the active repository; inspect Android CI and fix any compile/lint issues.
2. Verify on an Android device with Brave stable, especially system-DNS routing, full-screen coverage, and URL accessibility.
3. Replace starter lists with the user's final lists.
4. Evaluate broader browser enforcement and the built-in browser before promising page-level keyword/media blocking.
