# Atheer Pro integration — 2026-09-26

Owner-supplied base: `Oscar TV_v1.1.5 Pro.apk`, SHA-256
`2ca1fe7d3062f9fc5a3f95f2c8d3cc6ec199a2d052b6277f7d796ba9dc869a6c`.

The new base has three DEX files and an existing `com.pandora.core.AppFactory` startup component. Preserve its supplied code, native libraries, and assets. No new authentication or identity patches are part of this UI integration.

Recovered AWR feature from the user's `Atheer-startup-fix-1.apk` (57,941,471 bytes). That previous build was reported broken: Source World raised NoSuchMethodError and content requests returned 403. Do not call it a verified working release.

The previous loader calls `ClassLoader.getClassLoadingLock`, a Java SE API absent from Android. Replace it with an Android-compatible synchronization mechanism and compile the shell against the real Android SDK boot classpath.

New requested architecture: an internal DEX-only feature archive plus a separate resource/asset archive (preserving the existing SDK assets). No second application installation or external application launch for Source World. Preserve all recovered feature DEX classes, original resource IDs and native binaries; adapt activity contexts and keep Firebase registrar discovery isolated.

Brand: أثير / ATHEER, navy/gold cinema identity, redesigned home and navigation surfaces shared by host and source screens. Remove HiTV from navigation. Source World must return internally to the main host tabs.

Runtime catalogue/playback success is not established by APK assembly or signature verification. Device execution is required before claiming it works end to end.

## Build and verification

Toolchain: apktool 2.11.1, Android SDK platform 35 r02, build-tools 35 (core-lambda-stubs.jar and zipalign), R8 8.9.35, smali-dexlib2 3.0.9, apksig 8.5.2. Put android.jar and core-lambda-stubs.jar side by side; the other JARs share the compiler directory. Android core stubs are essential: using Java SE stubs previously allowed an API absent on ART.

`build_atheer_pro.py` takes `--host`, `--module`, `--apktool`, `--compiler`, `--android-jar`, `--keystore`, `--alias`, and `--output`. `AWR_KEYSTORE_PASSWORD` supplies the password privately. Recover the module from `assets/atheer/sources.apk` inside the owner-uploaded `Atheer-startup-fix-1.apk`, whose bytes were restored from the user's files.

The host's protected MainActivity is not modified. Lifecycle callbacks add or intercept Source World as the third item, preserve the original horizontal scrolling tab bar, and restore integration if the host asynchronously resets its items. The component factory inherits the owner's existing `com.pandora.core.AppFactory`; its supplied class and native libraries are unchanged. Guest activities use the isolated DEX loader and their own preserved resource IDs. The existing third-party SDK asset `assets/audience_network.dex` is preserved within the resource/asset pack; feature UI bytecode is in the DEX-only archive.

Host appearance changes retain all resource IDs: navy/gold palette, Cairo typography in both sets of screens, branded masthead, inset rounded hero, floating navigation, section action chips, rounded cards and rebranded splash/launcher assets. New logo was generated from the supplied reference with the built-in image tool: cinematic play/film-strip motif, Arabic أثير / ATHEER, midnight navy and gold, transparent background.

Structural checks: 10,637 host classes retained without modifying original host DEX; 86,525 feature classes retained; 117 context/theme adapters; original 408 native methods preserved; seven presentation classes changed; internal return navigation; no getClassLoadingLock or feature APK path in the shell. Firebase metadata remains isolated (14 host, 15 feature). The signing and 16 KB ZIP-alignment checks passed.

Limitations: no Android device/emulator execution was available. Static checks do not prove that all guest SDK initialization, original protected/native UI, resource loading, server access or playback works after integration. Existing optional background guest services have not been fully ported. The signer is new; this APK cannot update an existing installation signed with another key. The base already includes an arm64-only startup library; this integration does not add ABI support absent from that input.

Output SHA-256: `5da2de485b3d202964c37d5b51dc529e2981daafad60d4f41ecea61032fb2fb1`; 64820218 bytes.

Persistence status: source, logo and build reports are committed to GitHub. Saving the APK and private signing backup as durable user files failed during upload preparation. The download artifacts remain local to this session; the user should retain both. Do not assume the private signing key or output APK can be restored from GitHub.
