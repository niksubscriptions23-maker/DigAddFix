# DigAddFix

A fresh Android digital detox app. Version 0.2 supports the stable Android releases of Brave, Chrome, and Firefox through a browser-scoped DNS filter, with separate foreground app blocking.

## What works in this implementation

- Installed/enabled Brave, Chrome, and Firefox stable packages use a DNS-only Android VPN. Only virtual IPv4/IPv6 DNS addresses are routed into the filter; other apps retain their normal networking. No supported browser installed means no VPN is established.
- Browser DNS queries use Cloudflare's malware/adult family resolver over HTTPS. Bundled domain rules also block selected distracting services and their subdomains.
- Supported Google Search hosts map to Google's SafeSearch endpoint. Choose Google search and verify the setting separately in each supported browser.
- A bundled package list interrupts selected apps, including offline games, through an explicitly enabled accessibility service.
- Thirty other known browser packages/channels are on the app blocklist, including Edge, Samsung Browser, Opera/Mini, DuckDuckGo, Vivaldi, Tor, UC, Mi, Yandex, Firefox Focus, and beta/nightly variants. These are blocked because they are outside the protected set. The list is finite; unlisted browsers are not automatically blocked.
- An opaque full-screen accessibility overlay explains each detected block and closes automatically after five seconds. Repeated events for the same block do not extend the countdown. The underlying block remains active.
- Website overlays correlate the blocked DNS host with the protected browser's exposed current address. Address readers are scoped to exact native resource IDs for each browser. A blocked background resource should not interrupt an unrelated page.
- No account, ads, analytics, browsing-history storage, TLS interception, or page-body collection.

The blocklists are starter policy choices, not a classification of every app or website. Replace them when the final user-provided lists arrive.

## Install and set up

1. Download `DigAddFix-debug` from a successful [Android build](https://github.com/niksubscriptions23-maker/DigAddFix/actions/workflows/android.yml), extract the APK, and install it on Android 8.0 or newer. The debug APK is for testing; production signing is not configured yet.
2. Install/enable at least one of Brave, Chrome, or Firefox stable. Enable **browser protection** in DigAddFix and accept Android's VPN consent. The screen lists actual installed browsers and active DNS scope.
3. Use system DNS in Brave/Chrome. If Firefox uses DNS over HTTPS, disable it. Independent encrypted DNS or browser extensions/proxies can bypass this layer. Choose Google search in each browser; use the browser picker on the SafeSearch test and check that Filter is locked.
4. Enable DigAddFix accessibility after reading its disclosure. This enables listed-app/other-browser interruption and overlays. DNS filtering works independently of that permission.
5. Use the test button's picker to check each supported browser. Also confirm that ordinary browsing and other apps still work.
6. Optional: enable Android's always-on VPN setting for DigAddFix. Keep **Block connections without VPN off** so other apps retain Internet access.

Run the [device verification checklist](docs/DEVICE_VERIFICATION.md) before relying on this build.

## Coverage and limits

Only the three supported stable packages' system DNS lookups are covered, including their background lookups. Other browsers, other apps, and their embedded webviews are excluded from family DNS in v0.2. The app blocklist separately interrupts known unsupported browsers when accessibility and the app-block switch are enabled. Custom encrypted DNS, another VPN, cached/direct IP access, disabling permissions, browser extensions/proxies, and uninstalling the app can bypass this layer. DNS cannot classify an individual post, image, video, or an allowed domain's page content.

While active, the VPN refreshes its explicit browser list on supported-package installation, removal, replacement, or enable/disable changes, and checks it again when the setup screen resumes. Removing all supported browsers stops the VPN; install one and enable protection again. Reconfiguration can briefly interrupt browser DNS and does not promise a bypass-free transition.

Google enforcement covers only the host list in `FilterEngine.java`; other search engines are not forced into safe mode. Browser settings currently require user setup. A reserved keyword list is bundled but **page-text scanning is not implemented**; revisit it with the deferred built-in browser.

Website overlays require the browser to expose a committed current address through accessibility. Filtering can still block a request when the address is unavailable, but its overlay cannot be guaranteed in those modes. Firefox currently targets its native `mozac_browser_toolbar_url_view` address view; browser UI changes may require updated IDs and device testing. Android controls system bars, lock screens, and protected windows; full-screen coverage must be checked on the target device. The service returns to Home before showing the overlay, so closing it does not reveal the blocked app or page.

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
- [`app`](app/src/main/java/com/digaddfix/app): browser DNS service, installation support, accessibility interruption, overlay, and setup UI.
- [`BROWSERS.md`](docs/BROWSERS.md): exact supported package mapping, block policy, and primary references.
- [`PROGRESS.md`](docs/PROGRESS.md): saved decisions, verification results, and next work.

Permission changes and package lists should always be reviewed alongside the device checklist. Never turn missing supported browsers into an unrestricted VPN.
