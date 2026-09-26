# Atheer Pro integration — 2026-09-26

Owner-supplied base: `Oscar TV_v1.1.5 Pro.apk`, SHA-256
`2ca1fe7d3062f9fc5a3f95f2c8d3cc6ec199a2d052b6277f7d796ba9dc869a6c`.

The new base has three DEX files and an existing `com.pandora.core.AppFactory` startup component. Preserve its supplied code, native libraries, and assets. No new authentication or identity patches are part of this UI integration.

Recovered AWR feature from the user's `Atheer-startup-fix-1.apk` (57,941,471 bytes). That previous build was reported broken: Source World raised NoSuchMethodError and content requests returned 403. Do not call it a verified working release.

The previous loader calls `ClassLoader.getClassLoadingLock`, a Java SE API absent from Android. Replace it with an Android-compatible synchronization mechanism and compile the shell against the real Android SDK boot classpath.

New requested architecture: an internal DEX-only feature archive plus a separate non-executable resource archive. No second application installation or external application launch for Source World. Preserve all recovered feature DEX classes, original resource IDs and native binaries; adapt activity contexts and keep Firebase registrar discovery isolated.

Brand: أثير / ATHEER, navy/gold cinema identity, redesigned home and navigation surfaces shared by host and source screens. Remove HiTV from navigation. Source World must return internally to the main host tabs.

Runtime catalogue/playback success is not established by APK assembly or signature verification. Device execution is required before claiming it works end to end.
