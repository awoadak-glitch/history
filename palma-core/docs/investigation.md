# Independent PALMA content access

## Verified milestone — 2026-10-14 UTC

A standalone Node client fetched `api/v2/home.php?app_version=17` from the active
domain with HTTP 200 and JSON `status: success`: **32 banners and 28 sections**.
No Android process, emulator, PALMA installation, TDM or MX Player was running.
Only the three request headers from `src/iron.mjs` were needed. The Android
diagnostic header was not supplied.

Additional live requests returned HTTP 200 for series details, seasons, episodes,
episode details, film details and five content categories. Episode and film CDN
playlists returned 200; following a variant and requesting the first 1,024 video
bytes returned 206 (`video/mp4`). This verifies source access, before browser
playback verification.

## Trace through the supplied application

Reference APK SHA-256:
`e433d9e12233d7412cb624821ec29cc011b30a0bfb07eb84d18292db75eaabe1`.
The embedded original `assets/base.apk` has SHA-256:
`e15d2de82257e55a5ea7ffef7a78ec205caf0c02ddfa7b80673dba68006226d7`.

1. `uq0` loads `default_domain`, `backup_domains` and `poster_cdn` from Firebase
   Remote Config; the verified active origin is recorded in the client.
2. `api/ping.php` and the `yo` interceptor synchronize the request clock using
   the server's HTTP Date header.
3. `zo` signs the encoded URL path starting with `/api/`, **excluding the query**.
4. `IronFingerprint.d` creates a seconds timestamp and an eight-character UUID
   nonce, then invokes `nativeSign` in `libiron_fingerprint.so`.
5. The native certificate reader uses the APK signing block (v3, then v2).
   For this wrapped APK, the effective original certificate is in `assets/base.apk`.
6. `nativeSign` derives the request key from the public certificate fingerprint
   and package name, then calculates HMAC-SHA256 over `path|timestamp|nonce`.
7. The response is ordinary JSON. Home sections carry their own type, order,
   titles, display flags and items; those fields should drive the website.

Native x86_64 audit anchors: `nativeSign` at `0x20fd0`, certificate reader at
`0x217d0`, SHA-256 helper at `0x240a0`. Three successive SHA-256 calls follow
the two masks and reversal. The relevant library is identical in the outer
and embedded APKs. The JS implementation was validated against the live server.

The previous website used a stale default origin and optional bearer-token
authentication. Neither reproduced the actual PALMA request protocol.

## Playback finding

The older `api/watch_links/` route returned storage-origin links (403 on the
sample), whereas `api/episodes/show.php` returned fresh signed CDN links.
The original episode screen also calls `getEpisodeDetail`. The web client now
uses that detail response for both server display and playback, preserving
source indices. The `ua` query field in the provided `deep_link` supplies the
source's media User-Agent; no TDM application is needed to read those fields.

The CDN did not send browser CORS headers in the tested responses. Web playback
therefore uses the existing signed media proxy, including rewritten child HLS
playlists, initialization segments and encryption-key references where present.
The build generates a private deployment key shared by the Vercel Functions;
it is not in Git, `public/`, `dist/` or an API response.

Raw catalog/playback captures remain ignored; do not commit expiring links,
session material, APKs or derived request keys.
