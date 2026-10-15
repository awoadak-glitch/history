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
