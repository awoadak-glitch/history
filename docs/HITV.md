# HiTV integration — 2026-09-13

## Goal
Add a fifth **HiTV** tab to the existing Anime Witcher + Drama World build without disturbing the working Drama/MX/embed/download flows.

## Source analysis
- User supplied `HiTV_3.26.0.apks` (base + arm64 + xxhdpi split package).
- The supplied HiTV base is protected, so this integration does not modify or bypass its protection layer.
- The user's existing `awoadak-glitch/HITV-WEB` project already contains a validated native-compatible HiTV content adapter. The Android integration mirrors that adapter instead of inventing a different catalogue source.
- Content base used by that adapter: `https://web-api.hitvpro.com`.
- Request signing mirrors the existing project: 16-character random AES key, RSA PKCS#1 encrypted `aesKey`, MD5 `sign`, `currentTime`, AES-128-ECB response decryption.

## Native Android implementation
- `HitvApi.java`: direct HiTV catalogue/search/detail/playInfo client.
- `HitvExperience.java`: full-screen native HiTV interface, hero/rails, all/western/korean filters, search, detail page and episode selection.
- `HitvPlayer.java`: internal ExoPlayer-family playback by reusing the ExoPlayer runtime already present in Anime Witcher; MX Player remains a fallback if the internal player cannot be created.
- `WitcherTabs.java`: adds a fifth `HiTV` tab.
- `Ui.java`: adds the HiTV TV/play icon.

## Routes used
- Home: `/cms/web/hitv/homePage/album/page`
- Search: `/cms/web/hitv/movieDrama/searchWithKeyWord`
- Detail: `/cms/web/hitv/movieDrama/detail`
- Playback: `/cms/web/hitv/movieDrama/getPlayInfo`

Playback source normalization follows the user's HITV-WEB model: `sources`, `playList`, or a direct `url/playUrl/mediaUrl/file` entry, with quality labels shown before playback when multiple sources are available.

## Existing features preserved
Drama World playback/extraction, MX Player handoff, embed WebView behavior, TDM/1DM/ADM download options, Anime Witcher home content and all original DEX payloads are preserved. The new build replaces only the custom integration DEX (`classes29.dex`).

## Build
GitHub Actions run `34769342234` completed successfully from commit `bb7d828d6701ae21b93f680935751845297ff59a`.
- Patch SHA-256: `8e985d689795003401aee0869ca69e578e7283644d649658897061da1c1e452a`
- Custom DEX SHA-256: `91b29626c1a7e50faa29397fa13cb697eedb9ab26fe36342f7bb3382a1cab13a`
- Local signed test APK SHA-256: `57abc326915a832017f44efa3a209183adf1d32b08ff6f93ef3fce96b03356c0`
- APK v1/v2/v3 signatures verified.
- All non-signature payload entries match the previous embed+ADM test APK except `classes29.dex`.
- Stored-entry/native-library alignment verified.

## Testing limitation
The current execution environment could not resolve `web-api.hitvpro.com` (`EAI_AGAIN`), so live HiTV content/playback still requires real-device testing. Compile, DEX injection, APK integrity and signature validation succeeded.

## Current user request — preserve embed+ADM (2026-09-13)
- New authoritative input: `AWR-Anime-Witcher-Drama-embed-adm.apk`, SHA-256 `7859ea13210920393ac7b176908b73193e9b7f66f806297c2e6d2f7aea9df87b`.
- New HiTV package: `HiTV_3.26.0(1).apks`, SHA-256 `aa10e5366a9384708b1da055d9a56c9bd9a048a91ba8742322d37c078f3e2395`.
- Main at resume already contains a HiTV integration (`86cb4d4`). Continue that work; do not replay old Drama fixes.
- Raw DEX inspection of HiTV `base.apk` finds only 34 shell classes. Its layouts reference `com.captha.didymoi.module_video.VideoPlayer`, which is absent from exposed DEX and resides in the protected payload. Exact transplantation of this player is **not complete**. ExoPlayer in the host is an existing alternative engine, not proof that the HiTV player was copied.
- The host contains ExoPlayer.Builder in classes17.dex. Existing Drama sources differ from the approved embed+ADM baseline only in tab/icon integration; keep all Drama playback/download classes unchanged.
- Review pending: real HiTV API response shapes, playback/preview separation, subtitles, episode numbering and player lifecycle before claiming a working full-content tab.
