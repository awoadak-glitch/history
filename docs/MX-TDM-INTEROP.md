# MX Player / Oscar YNTR interoperability checkpoint

Date: 2026-09-28

## Supplied MX bundle

- Input: `MX Player_3.2.1.apks`
- SHA-256: `46cfe7008caa4e90f77cb1d16fcd5253fdb3767ece50003a57ff8ab4bd15ca81`
- Package: `com.mxtech.videoplayer.ad`
- Bundle members: `base.apk`, Arabic resources, arm64-v8a native split, and xxhdpi resources.
- All four APKs have the same original MX Technologies certificate SHA-256: `0d9b4c778672e753d45aba4f10df31e1d10bf75b1245469131ef01b82f0f91b6`.
- The base contains ten DEX files.

## Finding

The manifest accepts the standard `content`, `file`, `http`, `https`, `httplive`, `rtsp`, and `intent` schemes. Static inspection found no Oscar/YNTR resolver and no `com.tdm.manager` contract. MX can play a prepared media URI, but it cannot convert Oscar's opaque TDM deep link into that URI.

Changing MX's intent filter alone would only deliver the same opaque link to MX and reproduce the failure. Embedding the existing PALMA WebView resolver into MX would also reproduce the last failed attempt because it still lacks the proprietary resolver/session step performed by the working original handler.

## Correct integration point

Inspect and patch the handler that currently succeeds—expected package `com.tdm.manager`—at the point after it prepares the final HTTP/HLS/MP4 URL. That handler can then send the resolved URL, cookies, Referer, and User-Agent to `com.mxtech.videoplayer.ad`.

The installed original MX bundle cannot be updated with a locally modified bundle without the MX Technologies private key. Any patched MX bundle would require uninstalling the official MX app and re-signing every split, and may also break signature-bound components. No MX binary was modified at this checkpoint.
