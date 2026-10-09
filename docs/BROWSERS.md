# Browser policy in v0.2

The user's Brave preference is not a browser restriction. Support the stable releases below equally.

| Browser | Android package | Policy |
| --- | --- | --- |
| Brave | `com.brave.browser` | Browser-only family DNS and detected-current-page overlay |
| Chrome | `com.android.chrome` | Browser-only family DNS and detected-current-page overlay |
| Firefox | `org.mozilla.firefox` | Browser-only family DNS and detected-current-page overlay |

Only installed/enabled packages from this registry are added to the VPN. No supported package means no VPN. Non-browser apps retain normal DNS/network routing.

Other known browsers and beta/nightly variants use exact app-block rules in `app/src/main/assets/blocked-apps.tsv`. Their reason is that they are outside the protected set, not that every browser is harmful. App blocking requires accessibility and the app-block switch. Unlisted, renamed, cloned, OEM-specific, and future browser packages are not automatically recognized.

Website overlays use native URL-bar IDs scoped to the actual protected package. They never scan arbitrary webpage body text. They still need verification against installed browser versions and modes. Google SafeSearch mapping is shared by the supported DNS scope; independent encrypted DNS, other search engines, and extensions/proxies remain coverage limits.

Primary references checked on 2026-10-09:

- [Mozilla's Android browser package registry](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/android-components/components/support/utils/src/main/java/mozilla/components/support/utils/Browsers.kt).
- Official Play listings: [Chrome](https://play.google.com/store/apps/details?id=com.android.chrome), [Firefox](https://play.google.com/store/apps/details?id=org.mozilla.firefox), [Edge](https://play.google.com/store/apps/details?id=com.microsoft.emmx), [Vivaldi](https://play.google.com/store/apps/details?id=com.vivaldi.browser), [Tor](https://play.google.com/store/apps/details?id=org.torproject.torbrowser), [Mi Browser](https://play.google.com/store/apps/details?id=com.mi.globalbrowser), [Yandex](https://play.google.com/store/apps/details?id=com.yandex.browser), [Brave Beta](https://play.google.com/store/apps/details?id=com.brave.browser_beta), [Brave Nightly](https://play.google.com/store/apps/details?id=com.brave.browser_nightly).
