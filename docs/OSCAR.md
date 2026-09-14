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
