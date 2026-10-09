# DigAddFix

A fresh Android digital detox app, starting with Brave stable. Version 0.1 uses a browser-scoped DNS filter and separate foreground app blocking.

## What works in this implementation

- Brave stable (`com.brave.browser`) alone uses a DNS-only Android VPN. Only its virtual IPv4/IPv6 DNS addresses are routed into the filter; other apps retain their normal networking.
- Browser DNS queries use Cloudflare's malware/adult family resolver over HTTPS. Bundled domain rules also block selected distracting services and their subdomains.
- Supported Google Search hosts map to Google's SafeSearch endpoint. Choose Google as Brave's search engine and verify the setting in Brave.
- A bundled package list interrupts selected apps, including offline games, through an explicitly enabled accessibility service.
- An opaque full-screen accessibility overlay explains each detected block and closes automatically after five seconds. Repeated events for the same block do not extend the countdown. The underlying block remains active.
- Website overlays correlate the blocked DNS host with Brave's exposed current address. A blocked background resource should not interrupt an unrelated page.
- No account, ads, analytics, browsing-history storage, TLS interception, or page-body collection.

The blocklists are starter policy choices, not a classification of every app or website. Replace them when the final user-provided lists arrive.

## Install and set up

1. Download `DigAddFix-debug` from a successful [Android build](https://github.com/niksubscriptions23-maker/DigAddFix/actions/workflows/android.yml), extract the APK, and install it on Android 8.0 or newer. The debug APK is for testing; production signing is not configured yet.
2. Install Brave stable. Enable **Brave protection** in DigAddFix and accept Android's VPN consent.
3. In Brave, use system DNS rather than an independent Secure DNS provider. Choose Google search. Open the in-app SafeSearch verification link and check that Filter is locked.
4. Enable DigAddFix accessibility after reading its disclosure. This enables app interruption and overlays. DNS filtering works independently of that permission.
5. Test the harmless bundled blocked domain using the app's test button. Also confirm that ordinary browsing and other apps still work.
6. Optional: enable Android's always-on VPN setting for DigAddFix. Keep **Block connections without VPN off** so other apps retain Internet access.

Run the [device verification checklist](docs/DEVICE_VERIFICATION.md) before relying on this build.

## Coverage and limits

Only Brave stable's system DNS lookups are covered, including its background lookups. Other browsers, other apps, and their embedded webviews are excluded in v0.1. Custom encrypted DNS, another VPN, cached/direct IP access, disabling permissions, and uninstalling the app can bypass this layer. DNS cannot classify an individual post, image, video, or an allowed domain's page content.

Google enforcement covers only the host list in `FilterEngine.java`; other search engines are not forced into safe mode. Brave settings currently require user setup. A reserved keyword list is bundled but **page-text scanning is not implemented**; revisit it with the deferred built-in browser.

Website overlays require Brave to expose a committed current address through accessibility. Filtering can still block a request when the address is unavailable, but its overlay cannot be guaranteed in those modes. Android controls system bars, lock screens, and protected windows; full-screen coverage must be checked on the target device. The service returns to Home before showing the overlay, so closing it does not reveal the blocked app or page.

## Develop

Native Java Android app with a dependency-free JVM policy/DNS core. Build configuration: JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9, minimum SDK 26.

```sh
python3 scripts/check_core.py
./gradlew --no-daemon :core:check :app:assembleDebug :app:lintDebug
```

GitHub Actions runs the same checks and uploads a debug APK. The local core runner requires Java 17 with the compiler module and does not need Android SDK or Gradle downloads.

Relevant files:

- [`blocked-apps.tsv`](app/src/main/assets/blocked-apps.tsv): exact package, label, reason.
- [`blocked-domains.tsv`](app/src/main/assets/blocked-domains.tsv): domain, label, reason; includes subdomains at label boundaries.
- [`blocked-keywords.txt`](app/src/main/assets/blocked-keywords.txt): reserved for future page scanning.
- [`core`](core/src/main/java/com/digaddfix/core): DNS validation, packet handling, policy, TCP framing, and five-second timer.
- [`app`](app/src/main/java/com/digaddfix/app): Brave DNS service, accessibility interruption, overlay, and setup UI.
- [`PROGRESS.md`](docs/PROGRESS.md): saved decisions, verification results, and next work.

Permission changes and package lists should always be reviewed alongside the device checklist. Never turn a missing Brave installation into an unrestricted VPN.
