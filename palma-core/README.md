# PALMA independent content client

This work replaces the emulator approach. Its first milestone is fetching the
original PALMA catalog outside Android, before UI or player work.

## Investigation order

1. APK identity, active domain selection and server clock.
2. Native request signing and exact API path canonicalization.
3. Home lists, categories, series/movies and detail/episode requests.
4. Watch-link resolution and an independent player after catalog access works.

`tools/apk_profile.py` reads the public certificate from the supplied APK's
embedded `assets/base.apk`. It also records the outer certificate separately.
This distinction matters for the existing Android loader. The original APK is
not modified, launched or installed by this client.

`src/iron.mjs` ports the request calculation observed in the supplied native
library. Live verification on 2026-10-14 returned HTTP 200 for the original home
catalog (32 banners, 28 sections), content categories, series details and seasons.
See [the investigation record](docs/investigation.md). Video playback is still a
separate verification step.

## Run without Android

Node 22 or newer; no npm dependencies:

```sh
node palma-core/tools/probe.mjs
node palma-core/tools/probe.mjs api/series/show.php id=1667
node palma-core/tools/probe.mjs api/seasons/ series_id=1667
```

The client uses the verified public certificate identity and current API origin.
Set `PALMA_API_BASE` to change the origin when the application's Remote Config
changes. A probe prints a small summary and saves the response in the ignored
`captures/` directory. It never prints request signatures or playback URLs.

No application private key, user account, phone data, APK, native binary,
session token or raw playback capture is committed here.
