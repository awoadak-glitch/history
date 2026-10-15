# TDM 2.0.6 playback interoperability

Inspected input: the user-supplied `TDM_v2.0.6.apk`.
SHA-256: `2e03e63dbfd2236b3a46474328606b682af00b63f65bc8cb008af33e5b05ff46`.

## Observed behavior

- `VideoPlayerActivity.V(Intent)` reads `tdmvideo://play?url=…`, decodes the URL with `D(String)`, and takes `user_agent` before `ua`.
- `D(String)` accepts plain URLs, standard Base64 and URL-safe Base64. The HTTP URL's own signed query must not be decoded repeatedly.
- `VideoPlayerActivity.b0()` passes the player UA to Media3's HTTP data source; the default is `TDMPlayer/2.0.6`. `DefaultHttpDataSource.makeConnection()` uses identity transfer encoding for ordinary media requests and preserves byte ranges.
- `BrowserActivity.G(Uri)` handles `urlplayer` / `urlvplayer`. Hosts ending in `play` or `web` use `v(String)`: Base64 decoding, repeating XOR transport decoding, then URL decoding. This is URL wrapping, not media decryption. Other hosts carry a percent-encoded URL.
- `tdm` / `tdmbrowser` open a page in the original app. Our existing media resolver can inspect public embedded media and nested iframes; this does not claim support for every JavaScript-only host.
- `DownloadEditor` accepts the `url` parameter and `user_agent` / `ua`, separately from explicit Android Intent extras.
- In this supplied APK, `tdmvideo` does **not** read a `cookies` query parameter. The player's Cookie and Referer fields are populated by explicit Intent extras in the alternate entry path. No browser cookies, source cookies, or Authorization headers are imported or transmitted by this patch.

## Confirmed regression and correction

The 2.2 preview's source adapter omitted native UA headers when a direct HTTP URL existed or a deep link contained `cookies`. This differs from both TDM and the working 2.1 adapter. It also preferred a channel's `stream_url` even when the native link contained a different signed URL.

`server/tdm-link.js` now implements the observed transport formats. `server/source.js` keeps the native target and its UA together, with validated nonempty explicit source header overrides. Manifests, variants, keys and segments retain those playback headers. Cookie and Authorization forwarding remain disabled.

The tests include an independently generated XOR transport fixture, exact signed-query preservation, the B2 header regression, the different channel URL, downstream HLS headers, malformed input and the actual episode resolver boundary. Unit tests do not establish that an upstream server currently permits playback; packaged Windows decoding remains the release gate.

## Verification before this patch

Windows 2.2.1 entered and exited native fullscreen, opened the correct home-match channel and actor filmography, and decoded Witcher Naruto 001 / PD 1080p (1440×1080; 1421.563 seconds). Native PALMA requests failed and channels did not play. These failures must not be described as fixed until a new packaged-app check succeeds.

## Packaged Windows 2.2.2 result

Commit `cb1285b0dfb0a4c058bb5fa35b052154f34b8a03`, run `38768117548`, tested at 2026-10-15 16:22 UTC:

- All 37 unit tests passed and the complete Windows ZIP built successfully.
- The native PALMA episode request, with the restored TDM UA, redirected to the source's notice video and was rejected by our media validator. It is not a playable episode. Channel playback also failed.
- Home images, match-to-channel navigation, actor filmography, native fullscreen entry/exit and actual Witcher Naruto 001 PD 1080p decoding passed. Anime duration was 1421.563 seconds and 78 frames decoded at 3.059 seconds.
- A raw channel URL in the inspected API sample differed from its deep-link URL by a fragment; the HTTP origin and path were identical. The native-first selection is correct, but that fragment difference does not explain the upstream refusal.
- No Cookie or Authorization header was forwarded. The current API includes an `ot` Cookie value in native links; its values and signed URLs are not stored in this repository. Testing that credential against its declared source hosts remains blocked by the earlier automatic approval review until the user explicitly authorizes that transmission.
- This build was preserved as a workflow artifact, not published as a working release. Full report: `docs/palma-windows-2.2.2-verification.json`.

## Authorized source-session repair (2.2.3)

After reviewing the exact two source destinations, the owner explicitly authorized transmission of the source-issued playback Cookie on 2026-10-15. The adapter now carries the returned `ot` value only for PALMA and only on its matching HTTPS origin (`cdn.mediaoscar.com` or `still-dust-c9ae.manon-lol2000.workers.dev`). It does not import browser/account cookies. Foreign redirects and cross-origin playlist resources lose the credential, including redirects between the two approved hosts.

Media tickets use authenticated encryption so credentials are not readable in playlist URLs. Downloads restore the same bound context; no credentials are persisted in download history. Local tests cover host binding, redirects/resource transitions, encrypted-ticket tampering and both watch/download resolution. The Windows gate also decodes an actual B2 film in addition to the existing series/channel/anime flows.

## Packaged Windows 2.2.3 result

Commit `609e4ee5c007ad7109a8e2c90071c2ab5685dea3`, run `38770379521`, tested on 2026-10-15:

- PALMA episode 43120 decoded at 1920×1080 with duration 2876.56 seconds; movie 4528 / B2 decoded at 1920×800 with duration 6223.09 seconds. These are actual video frames, not a replacement notice.
- HLS master/variant/segment delivery, a two-segment 4,250,646-byte MP4 download and FFmpeg decoding passed. Witcher Naruto 001 PD1080p, native fullscreen, matches and actor filmography also passed.
- The remaining failure was channel 63: the resolver rejected the stream URL before opening the player. The build was preserved as an artifact, not published as a working release.

## Confirmed channel routing correction (2.2.4)

Sanitized Windows diagnostic run `38808893030` found that all five qualities for channel 63 returned HTTP 302 from the approved HTTPS gateway to `http://assets.pushyourcss.world:8080`. The existing HTTP validator permitted only ports 80 and 443. It therefore rejected the live destination with `INVALID_URL` after the source session had already been accepted.

Port 8080 is now supported alongside 80/443. Every redirect still requires a public DNS result with address pinning; private addresses, embedded credentials and other ports remain rejected. The source Cookie is stripped on the redirect to the live host. A transport regression test exercises that redirect and asserts that only the UA survives. The UI verification now reports the actual resolver/player error immediately instead of a generic 80-second timeout. Packaged live decoding remains the publication gate.

## Released Windows 2.2.4 — verified

Commit `5c0e85beabf4e35c9318ff684a7ec9a425684cbd`, successful Windows run `38809247179`, tested at 2026-10-15 21:23 UTC:

- All 44 unit tests passed. The packaged executable decoded the native PALMA episode (1920×1080), B2 film (1920×800), channel 63 (1920×1080, live) and Witcher Naruto 001 PD1080p (1440×1080). The channel screenshot shows the actual beIN Sports 1 broadcast.
- Native fullscreen entry/exit, home-to-match navigation (match 391399 → channel 65), cast-to-filmography (actor 8532 → 21 works), source images, HLS ranges and download/remux/decode all passed.
- Full sanitized evidence: `docs/palma-windows-2.2.4-verification.json`. Screenshots and the same report are attached to the release. This verifies the sampled current sources; it does not guarantee every upstream server or JavaScript-only embed. The separately configured Drama World provider remains unavailable.
- Released ZIP: https://github.com/awoadak-glitch/history/releases/download/palma-windows-v2.2.4/PALMA-VISION-Windows-2.2.4.zip
- ZIP size: 182,270,393 bytes. SHA-256: `3452d4854c3cd6a5777b7d81a89dfb90f4ff6ec231bda28ed1ab281650b5143e`.
