# Protection lock (v0.3)

This milestone adds a recoverable lock. It does not make an ordinary Android phone tamper-proof. Device behavior has not yet been tested on a phone.

| Capability | Personal guard | Full device owner, Android 11+ |
| --- | --- | --- |
| Disable DigAddFix's browser/app controls | PIN or recovery code required | PIN or recovery code required |
| Known stores and installer/removal screens | Accessibility interruption, best effort | OS installation restriction plus interruption |
| VPN and Private DNS settings | Recognized native screens only | OS configuration restrictions |
| Uninstall, force-stop, clear data | Recognized screens only | Protected for DigAddFix and all three supported browser packages |
| VPN restart | Attempt when granted; Android can reject a background start | Owner-configured always-on VPN, lockdown **false** |
| Browser preferences/extensions | Known activity/resource IDs and internal addresses | Same UI guard; no universal browser policy enforcement |
| Extra users/user switching | Recognized screens only | OS restrictions; existing profiles are outside tested coverage |

The VPN still routes only the installed supported browsers' virtual DNS addresses. No default route, device-wide DNS change or global lockdown is added. App blocking is separate. DNS queries from other apps are not sent to the family resolver. Wi-Fi/Bluetooth, phone/emergency calling, lock screens and runtime permission consent are not intentionally interrupted. Entire Android Settings and PermissionController packages are not blanket-blocked.

## Personal-phone setup

1. Set each installed stable Brave, Chrome and Firefox to use system DNS, remove independent proxy/DNS/extensions that bypass filtering, choose Google search and verify SafeSearch. Do this before locking; DigAddFix cannot reliably audit those settings.
2. Enable browser protection, accessibility and the app-block switch. Confirm filtering in each browser and normal networking in another app.
3. Open **Protect your setup → Set PIN and lock protection**. Use 6–12 digits; repeated and sequential PINs are rejected. A trusted person can keep the PIN if desired.
4. Write down the complete recovery code outside the phone. It has 128 random bits, is displayed once and requires confirmation of its final eight characters before activation. Screenshots, saved form state and autofill are disabled in credential flows.
5. PIN/recovery release restores this app's managed policy baseline where applicable and releases guarded controls. Existing filtering/app blocks continue until intentionally disabled. Release before installing or updating apps.

After five failed attempts the retry delay begins at 30 seconds and increases up to 15 minutes. Attempts and remaining wait are persisted before verification. Elapsed time is used on the same boot; after reboot, clock rollback cannot shorten the saved delay. The PIN and recovery code are stored only as independent salted PBKDF2-HMAC-SHA256 records (600,000 iterations, 16-byte random salt, 32-byte derived key); hashing runs off the main thread. No plaintext credential, recovery code or browsing history is persisted. A rooted/debuggable environment can still compromise app-private state; a short PIN is not an offline-proof secret.

The native-screen classifier is deliberately bounded. Chromium settings use a known activity class. Firefox preference widgets and extension screens use exact native view IDs; a generic RecyclerView is not enough. Only committed URL-bar nodes are read for internal settings addresses. Native view metadata is checked without reading page bodies or settings labels. OEM settings, Compose screens, browser updates, shortcuts, background installers and unrecognized stores can evade personal interruption. Accessibility removal, safe mode, ADB, root, reset and independent encrypted DNS/proxies remain bypass paths. A locked supported browser is interrupted if the in-process filter is unavailable, but accessibility removal also removes that safeguard.

## Managed test device

Backup note: legacy backup is disabled and explicit Android 12+ rules exclude every app-data domain from cloud backup and device-to-device transfer. Credentials, retry state and device-specific policy journals must stay on their original device. See [Android backup behavior](https://developer.android.com/identity/data/autobackup).

Use a dedicated Android 11+ test device or emulator with no valuable data. A work profile or legacy Device Admin permission is insufficient. DigAddFix does not start provisioning or reset a device. Normal production enrollment is a separate workflow, commonly during fresh device setup; no QR enrollment or production deprovisioning is implemented here.

For an eligible development device, Android documents ADB device-owner testing. Install the debug APK and supported browsers before locking. With no accounts/other owner/users blocking eligibility, an administrator may explicitly run:

```sh
adb shell dpm set-device-owner com.digaddfix.app/.ProtectionAdminReceiver
```

This command is documentation only; it was not run. If Android rejects eligibility, stop and examine its error. Do not remove accounts or reset a personal phone just to satisfy this guide.

Enable the normal filters, save the recovery code, and lock protection. The UI must say **Managed policies confirmed**. A journal captures this admin's previous restriction values, uninstall flags, user-control package list and always-on VPN package before any mutation. Conflicting always-on VPN/global lockdown is rejected. The owner VPN is configured before VPN changes are restricted. Recovery verifies restoration and retains the journal/lock when restoration fails so the operation can be retried. **Repair active protection** reapplies an active lock; it does not require the PIN because it only strengthens the authorized setup.

Managed installation restrictions prevent installs/updates by the owner too. Release the lock for updates, then recheck browsers and lock again. Reboot/package-replacement handlers reconcile an active lock, with restart failures reported. No safe-boot, debugging, factory-reset, device-wide DNS, lock-task or blanket settings restriction is set in this milestone. Owner mode therefore adds OS resistance, not a universal bypass guarantee. Other profiles, recovery, root, permissions and per-browser configuration still need device verification.

Releasing the PIN lock **does not remove the device-owner role**. On a debug device-owner test build, **Remove test management after unlock** provides a separately confirmed test teardown using Android's deprecated `clearDeviceOwnerApp` API. Android describes this API as testing-only and best effort; unrelated policies may remain, and reinstating ownership can require fresh setup. It is unavailable on production builds or while locked. No reset is performed. Production management removal needs its own reviewed workflow.

## Primary references

- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager): `setAlwaysOnVpnPackage`, `setUninstallBlocked`, `setUserControlDisabledPackages` (API 30), getters and testing-only owner removal.
- [UserManager restrictions](https://developer.android.com/reference/android/os/UserManager): installation, VPN, Private DNS and user restrictions. `DISALLOW_INSTALL_APPS` also prevents owner installations; VPN configuration restriction interacts with user-configured VPNs on Android 12+.
- [Dedicated devices](https://developer.android.com/work/dpc/dedicated-devices) and [development cookbook](https://developer.android.com/work/dpc/dedicated-devices/cookbook#development).
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): slow salted hashing and the PBKDF2-HMAC-SHA256 work factor. PBKDF2 is available natively on the app's supported Android versions; this is not a FIPS certification claim.
- [AOSP Settings activity classes](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/Settings.java), [PermissionController manifest](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/AndroidManifest.xml).
- [Chromium settings activity](https://github.com/chromium/chromium/blob/main/chrome/android/java/src/org/chromium/chrome/browser/settings/SettingsActivity.java).
- Mozilla [SettingsFragment](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/settings/SettingsFragment.kt), [switch widget](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/fenix/app/src/main/res/layout/preference_material_switch.xml), [add-on list](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/fenix/app/src/main/res/layout/fragment_add_ons_management.xml), [add-on settings](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/fenix/app/src/main/res/layout/fragment_add_on_internal_settings.xml). Source IDs/classes are candidates for device verification, not proof of current UI exposure.
