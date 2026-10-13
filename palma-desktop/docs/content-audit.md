# PALMA desktop feasibility and verification record

## Input and original interface

The supplied `PALMA-VISION-1.1.7-NO-ADS-DOWNLOAD-FIX.apk` is 45,038,107 bytes.
SHA-256: `e433d9e12233d7412cb624821ec29cc011b30a0bfb07eb84d18292db75eaabe1`.
Package: `com.drama.mp4`, version code 17 / version name 1.1.7.
The Windows launcher preserves these APK bytes and opens
`com.drama.mp4/com.drama.mp4.ui.splash.SplashActivity`.
The APK remains the owner of navigation, content, authentication and playback.
The desktop setup screen is WPF; the content UI runs inside BlueStacks.

## Findings from the APK and source-server inspection

1. The app obtains domains from Firebase Remote Config, including `default_domain`,
   `backup_domains` and `poster_cdn`. The inspected active primary was
   `https://api.ossccaarr111.online/`; poster CDN was `https://poster.ostv-11-33.xyz/`.
   These are an inspection snapshot, not fixed future configuration.
2. A normal JSON request to the current primary's
   `/api/v2/home.php?app_version=17` returned HTTP 403 with
   `{"status":"error","message":"Forbidden"}`. Therefore replacing the old
   `ostvapp.cam` hostname alone does not establish web access.
3. Native requests include `X-Iron-Sig`, `X-Iron-Ts`, `X-Iron-Nonce` and
   `X-Iron-Diag` produced using Android context. A configured web bearer token is
   not evidence of native API authorization. The launcher does not forge these
   headers, disable integrity checks, alter certificates, or rotate hosts to
   evade rejection. The original app makes its own requests.
4. The merged APK uses `com.pandora.core.AppFactory` and `libpandora-core.so`.
   That loader library exists only under `lib/arm64-v8a`, despite other libraries
   also having x86 builds. Android 9+ and installation with `--abi arm64-v8a`
   are required for this wrapper. An x86-only install can fail before loading UI.
5. Some playback flows explicitly open
   `com.tdm.manager/com.tdm.manager.browser.BrowserActivity` with resolver extras,
   then `com.mxtech.videoplayer.ad`. Compatible modified TDM/MX builds are a
   separate dependency. Package presence alone does not prove bridge compatibility.
6. The launcher can copy those installed APK/split APK files from one USB-authorized
   phone and install them into the selected BlueStacks instance. It does not copy
   private app data, tokens, accounts or viewing history. File-based installation
   is also supported. Android verifies install signatures; conflicts abort.

## Web and Windows playback are separate paths

The existing web UI and AWR MX v7 Windows integration remain in `palma-web` and
`awr-mx-desktop`. This new launcher does not claim that PALMA's native TDM intents
are redirected to AWR MX on Windows. They execute inside the Android runtime.
The anime gateway's previously successful catalog/resolve checks do not establish
video playback for every host or repair the protected native PALMA catalog.

## Build and verification boundaries

The Windows CI workflow builds .NET Framework 4.8 WPF, exercises real Windows
argument parsing, checks the original APK binary manifest fixture, rejects malformed
manifest data, validates supported Android/ABI combinations and renders the actual
WPF setup interface. It packages only the verified build after these gates pass.

CI does **not** install BlueStacks or validate the native content service. Android
hardware virtualization and a user-authorized phone are unavailable in that runner.
The following remain unverified until an actual Windows/BlueStacks session:

- This APK's complete startup and any emulator-specific restrictions.
- Native home/source lists and server authorization in that runtime.
- The user's modified TDM/MX versions and full episode/movie playback.
- USB drivers and the particular BlueStacks installation on the user's laptop.

No claim is made that every source works or that successful process startup proves
content playback. The launcher shows these boundaries in the UI and diagnostics.

## Official runtime references

- https://support.bluestacks.com/hc/en-us/articles/360058929011-What-is-Application-Binary-Interface-ABI-in-BlueStacks-5
- https://support.bluestacks.com/hc/en-us/articles/23925869130381-How-to-enable-Android-Debug-Bridge-on-BlueStacks-5

Use the vendor installer. BlueStacks binaries and Google's Android emulator system
images are not redistributed in this package.
