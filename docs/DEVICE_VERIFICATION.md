# Device verification

These checks have not been run in this workspace. Record device model, Android version, Brave/Chrome/Firefox versions, source commit, and results before claiming device coverage. Repeat browser checks for every installed supported stable browser.

## Browser isolation and DNS

1. Enable browser protection with accessibility initially off. Using the app's browser picker, confirm `digaddfix-blocked.test` fails in Brave, Chrome, and Firefox; `example.org` should load normally in each.
2. Run the family resolver diagnostic, then verify actual routing in each browser using Cloudflare's harmless adult-category test `nudity.testcategory.com`. A direct diagnostic alone does not establish browser coverage.
3. Check ordinary networking in other apps and embedded webviews. With app blocking temporarily off, check an unsupported browser too. Repeat during family resolver unavailability; excluded packages should keep normal DNS/network paths.
4. Test Wi-Fi, cellular, and an IPv6-capable network, including network switching. Test large UDP replies requiring TCP DNS fallback.
5. Test Chrome-only and Firefox-only devices/configurations with Brave absent: protection should start. With all three absent/disabled, activation must report the missing supported browser and must never establish an unscoped VPN.
6. While active, install/remove/disable/re-enable a supported browser, update it in place, and switch foreground browsers. Check the displayed scope and filtering in the new browser. Removing the last supported browser should stop protection; installing one afterward requires activation again. Reopen the setup screen to check reconciliation.
7. Test permission removal, manual stop/restart, service restart, reboot with always-on, and another VPN taking over. Keep Android's global **Block connections without VPN** disabled; confirm a clear status if enabled.
8. Record custom Secure DNS, Firefox DNS-over-HTTPS/extensions, cached/direct IP navigation, custom tabs, and private-mode behavior as coverage limits; do not infer protection from a running VPN icon.

## Google SafeSearch

1. Set each supported browser to system DNS and Google search. Open `www.google.com/safesearch` separately in Brave, Chrome, and Firefox, and verify Filter is locked.
2. Verify supported country hosts such as `www.google.co.in`, plus image and video search settings.
3. Check that unsupported engines are explicitly described as unenforced. Do not claim Brave Search is forced safe.

## App and website overlays

1. Enable accessibility after reading the disclosure. Launch a starter-list app, including an offline game. Confirm return to Home, an opaque full-screen pause, and removal after five seconds.
2. Trigger repeated events for the same block during the countdown. They must not reset or extend its five-second deadline.
3. Reopen the app after the overlay disappears; it should still be interrupted. Turn the app-block switch off and confirm that policy is intentionally disabled.
4. Enable DNS and navigate to the harmless local blocked domain in each supported browser. Confirm the current-page overlay; repeat with the family category test. Validate the Firefox address-node ID on the installed version.
5. A blocked background/subresource request on an unrelated allowed page must not show a website overlay. Typing a partial URL must not interrupt editing.
6. Check expanded/collapsed URL bar, private mode, custom tabs, large font settings, screen rotation, display cutouts, gesture navigation, split screen, and picture-in-picture. Record any mode where the committed URL is unavailable.
7. Check full-screen coverage, system bars, touch interception, and exact dismissal timing on the target device. Android may reserve protected surfaces. Lock screens and permission dialogs must remain usable.
8. Disable/re-enable accessibility and DNS separately. The UI must accurately report what is active; stopping DNS must not claim browser protection is still running.
9. Launch installed known unsupported browsers such as Edge, Samsung Browser, Opera, DuckDuckGo, and Chrome/Firefox beta variants. Confirm return to Home and the five-second app overlay. Supported stable browsers must remain usable. Disabling the app-block switch intentionally allows unsupported browsers; the UI must report that app blocking is off.

## Data and permissions

Confirm no page bodies, search text, passwords, or browsing histories are stored or logged. URL-bar parsing should only use the committed address node. Review the manifest package queries whenever the app list changes.
