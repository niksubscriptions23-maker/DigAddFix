# Device verification

These checks have not been run in this workspace. Record device model, Android version, Brave version, source commit, and results before claiming device coverage.

## Browser isolation and DNS

1. Enable Brave protection with accessibility initially off. Using the app's harmless blocked-domain button, confirm `digaddfix-blocked.test` fails in Brave; `example.org` should load normally.
2. Run the family resolver diagnostic, then verify actual Brave routing using Cloudflare's harmless adult-category test `nudity.testcategory.com`. A direct diagnostic alone does not establish Brave coverage.
3. Check ordinary networking in other apps, other browsers, and embedded webviews. Repeat during family resolver unavailability; those apps should keep their normal DNS/network paths.
4. Test Wi-Fi, cellular, and an IPv6-capable network, including network switching. Test large UDP replies requiring TCP DNS fallback.
5. Brave missing: activation must report the missing app and must never establish an unscoped VPN.
6. Test permission removal, manual stop/restart, service restart, reboot with always-on, and another VPN taking over. Keep Android's global **Block connections without VPN** disabled; confirm a clear status if enabled.
7. Record custom Secure DNS, cached/direct IP navigation, custom tabs, and private-mode behavior as coverage limits; do not infer they are protected from a running VPN icon.

## Google SafeSearch

1. Set Brave to system DNS and Google search. Open `www.google.com/safesearch` and verify Filter is locked.
2. Verify supported country hosts such as `www.google.co.in`, plus image and video search settings.
3. Check that unsupported engines are explicitly described as unenforced. Do not claim Brave Search is forced safe.

## App and website overlays

1. Enable accessibility after reading the disclosure. Launch a starter-list app, including an offline game. Confirm return to Home, an opaque full-screen pause, and removal after five seconds.
2. Trigger repeated events for the same block during the countdown. They must not reset or extend its five-second deadline.
3. Reopen the app after the overlay disappears; it should still be interrupted. Turn the app-block switch off and confirm that policy is intentionally disabled.
4. Enable DNS and navigate to the harmless local blocked domain in Brave. Confirm the current-page overlay; repeat with the family category test.
5. A blocked background/subresource request on an unrelated allowed page must not show a website overlay. Typing a partial URL must not interrupt editing.
6. Check expanded/collapsed URL bar, private mode, custom tabs, large font settings, screen rotation, display cutouts, gesture navigation, split screen, and picture-in-picture. Record any mode where the committed URL is unavailable.
7. Check full-screen coverage, system bars, touch interception, and exact dismissal timing on the target device. Android may reserve protected surfaces. Lock screens and permission dialogs must remain usable.
8. Disable/re-enable accessibility and DNS separately. The UI must accurately report what is active; stopping DNS must not claim browser protection is still running.

## Data and permissions

Confirm no page bodies, search text, passwords, or browsing histories are stored or logged. URL-bar parsing should only use the committed address node. Review the manifest package queries whenever the app list changes.
