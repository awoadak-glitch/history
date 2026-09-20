# Device failure and native identity review — 2026-09-20

The user tested `AWR-World-IronFingerprint-test.apk` (SHA-256 `e79a2a945b4a73ee0a4f62f46d2901f14d3ce09a7784d6828e8a01bb6fef0388`). Their channels and episode screenshots show `IRON_INTEGRITY` and HTTP 403. This is evidence of a **local native integrity rejection**, not a missing library, a JNI lookup failure, or a six-second startup token. `OscarAuth` deliberately does not call the signer after that rejection. These failed requests therefore do not test whether the backend accepts a signed request from the new certificate.

## Static findings in the unchanged owner-supplied native library

Source APK: Oscar TV 1.1.4, SHA-256 `e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7`.

Reviewed the exported native integrity function in the x86_64 library and its certificate helpers. The first comparison expects application package `com.drama.mp4`. The current APK actually installs as `com.anime.witcher`. That mismatch is sufficient to return false. Merely moving Oscar DEX classes into the current APK does not change the Android application ID.

The subsequent helpers read certificate material from the installed APK signing block and Android PackageManager (`getPackageInfo`, `signingInfo` / `signatures`, `getApkContentsSigners`, `toByteArray`) and compare derived digests. A process-map check also participates in the result. This review does **not** establish that any new certificate is accepted by the backend, or that the original signing key is pinned locally. No signing secret was extracted; no integrity branch, context, certificate, or native library was modified.

## Proposed integration variant

Evaluate a real application-ID variant of the existing AWR app, using `com.drama.mp4` in AndroidManifest.xml. Keep actual Context and certificate reporting, native verifier, and native signer unchanged. Preserve AWR/Drama navigation and the existing internal Source World interface. Review providers, permissions, resource package names, and compiled package-dependent strings before producing an APK.

This is a separate install identity from `com.anime.witcher`, **not an in-place AWR update**. It also conflicts with an installed original Oscar package signed by another key. Do not uninstall either app or claim data migration. Installing and testing should use a spare device/profile without a conflicting package.

No emulator, ADB device, or Android runtime is available in this workspace. Any resulting artifact remains a candidate for phone testing until actual catalogue, episode, and playback requests succeed. Re-signing alone cannot prove server acceptance.
