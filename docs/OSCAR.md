# Oscar TV integration — 2026-09-14

The user redirected work from HiTV to the supplied Oscar TV 1.1.4 APK. Add Oscar as an independent section, matching the source's catalogue/detail/server organization with Anime Witcher colors and MX playback. Preserve the user's existing Drama flows and previous tabs.

- Source package: com.drama.mp4.
- APK SHA-256: e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7.
- The exposed DEX includes ApiService Retrofit annotations and named content models. The exact route/query map is saved in artifacts/oscar-api-map.json.
- Original UI has home sections, movie/series catalogues, filters, detail pages, seasons, paginated episodes and separate watch/download lists. Do not replace search with cached home items.
- Movies contain watch_links/download_links; series expose seasons and episode details with watch_links/download_links. Link models carry url, deep_link, server_name and quality; follow the actual link routing before assuming URLs are playable media.
- Public catalogue requests from this environment returned HTTP 403, Cloudflare error code 1010. No live catalogue or playback success has been verified. No bypass of Cloudflare or fingerprint checks was attempted.
- Previous HiTV diagnosis identified catalogue results mixed into keyword search; no HiTV behavior changes were made before the user's switch to Oscar.

Implementation and final build are in progress. The last completed APK before Oscar is the HiTV test build documented in RESUME.md. Do not describe Oscar as complete until its adapter, UI, media handoff and build have been validated.

## Source World checkpoint — 2026-09-19

The user expanded the request to all Oscar primary tabs, a scrollable nested tab bar, a return-to-main-tabs action, new branding and a night theme. The implementation now replaces the source experience added by remote commit 987416e, preserving that commit in history.

Implemented:
- Independent full-screen Source World (`عالم المصادر`), original tabs covered while open; source tabs scroll horizontally. Main tabs action dismisses this surface.
- Home, series, movies, channels, matches, anime, wrestling, favorites, search, settings and return-to-main navigation.
- Typed catalogue models, original route parameters (`app_version=14`), paginated search, server filter metadata, details, seasons, paged episode lists with search/sort, separate watch/download rows, local favorites, match dates and channels.
- Video content-type verification before MX handoff, standard HTML video/source elements, HLS master quality selector with automatic quality retaining all rendition groups, MX package `com.mxtech.videoplayer.ad`, existing download-manager chooser.
- Source-defined opaque app deep links remain external source links, never invented as MX media URLs. Original source errors remain visible.
- App branding `عالم AWR`, gold wolf icon, dark colors in normal and night resources, refreshed anime toolbar/cards. Existing host, Drama and HiTV code preserved by the selective DEX merger.

Validation is fixture/local-server based. Source server previously returned 403/Cloudflare 1010; no successful live Oscar catalogue or stream has been observed here. This build does not promise every source feature: source-account sync/comments, predictions, all tournament/statistics subpages and opaque source-player decoding are not implemented. The settings screen contains working local controls, not a replica of all source settings. These limits must be stated when delivering the APK.

Do not publish tokens, signing passwords, fingerprint secrets or decompiled third-party source. The regression suite is `python3 tooling/test.py --suite OscarFlowTest`. `tooling/build_oscar.py` compiles, merges, applies branding and verifies signing/alignment/preservation. The existing integration build prepares `build/generated` configuration first.
