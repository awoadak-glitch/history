# Windows playback after OscarTV 1.1.6

Status: NOT READY FOR RELEASE. Windows 2.2.7 packaging succeeded, but all five
native Windows playback/download checks failed on 2026-10-22. The source
returned a short rejection notice instead of the requested content. Successful
V3 registration, encrypted detail/resolve, HLS HTTP 200, and MPEG-TS bytes did
not establish content playback. Any shared package is experimental only.

## Cause and corrected diagnosis

Windows 2.2.6 still loads the catalog, but legacy playback detail reports
`update_required: true`. A version header alone does not establish the new
session. Playback now uses `api/v3/devices/*` and `api/v3/resolve.php`.
Account login under `api/auth/*` is separate and was not needed in the live test.

The earlier registration probe omitted `attest` and received HTTP 403 with
`attestation failed`. It did not establish that Windows was unsupported.
With the recovered application identity, registration with platform `windows`
returned HTTP 200, `status: success`, `sig_alg: ecdsa`, and `trust: basic`.
No server changes were needed for this test.

## Recovered release identity

Supplied APK: `com.drama.mp4`, version name `1.1.6`, code `18`, SHA-256
`b6b23d4f667d4662281bb2a8363d3821d36aa37ecc13e06384227dd96a45326b`.
Public signer-certificate SHA-256:
`6e2fcda8631eb49ebcba4ca8ef4c597abe84654c7d3e8096db32bdd21ecf763f`.

`nativeAttestationData` builds JSON with `file`, `raw`, `pm`, `hook`, and `acf`.
The native APK readers hash the first signer certificate, through stdio and
direct reads respectively. The package-manager reader hashes that certificate.
An independent signing-block parser and Androguard obtained the same digest.
The manifest declares `androidx.core.app.CoreComponentFactory`, represented as
`ok` by the native serializer. `registration-profile.mjs` documents the legacy
compatibility identity. It is release metadata, not a Windows integrity
guarantee or hardware attestation.

The APK has a software P-256 key fallback. The Windows client creates its own
key and omits Android hardware `attest_chain` and its challenge. The platform
stays Windows. The source version `1.1.6` is separate from desktop `2.2.7`.
The public certificate identity is not the APK's private signing key.

## Implementation and evidence

Each installation creates its own P-256 key and device ID. Electron safeStorage
protects source credentials, separated by API origin. The server issues each
installation's secret; no shared secret is shipped or logged.

V3 signs the method, path, query hash, timestamp, nonce, and body hash.
AES-256-GCM responses use a request-bound response key. The resolver requests
V3 details then resolves the selected link ID. Source-issued cookies remain
bound to approved media origins and are removed on cross-origin redirects.
Movies, episodes, anime, channels, wrestling, and match videos retain separate
source identifiers. Premium, update, player, tamper, and adblock rejections
remain enforced. Refused registration retains its key and has a retry cooldown.

Live diagnostic for movie 4655: registration HTTP 200; encrypted detail and
resolve HTTP 200; no explicit update requirement or guard rejection. The HLS
and MPEG-TS response nevertheless contained the source's rejection video.
Account login was not called. The remaining playback rejection is unresolved.

## Validation and release gate

65 local web/desktop unit tests pass. They cover signatures, encrypted replies,
time correction, persistence, origin separation, application identity, link
mapping, guards, and session propagation. They do not replace Windows testing.

The branch workflow builds Windows 2.2.7 and runs `--self-test-v3` in the packaged
Electron app. Its intended coverage is movie HLS, movie B2, episode decoding
and seeking, a live channel, and a short HLS download remuxed and decoded with
bundled FFmpeg. Run 39792799623 failed at the first movie, so later cases were
not reached. The follow-up run 39874740424 tested all five cases independently
on Windows using commit `e2bc495bfb7453271a93652282facbe786c531fb`:

| Check | Result |
| --- | --- |
| Movie HLS | Failed: short replacement video |
| Movie B2 | Failed: short replacement video |
| Series episode | Failed: short replacement video |
| Live channel | Failed: recorded replacement video |
| V3 download | Failed: short replacement playlist |

The report was recorded at `2026-10-22T23:56:17.749Z` under Electron 44.7.2,
Node 24.21.0, win32. Movie HLS and channel screenshots show the same source
rejection notice. The B2 screenshot was not saved because the screenshot-name
validator does not accept digits; its failure is still recorded in the report.
Reports contain status, dimensions, durations and frame counts, never signed
URLs or credentials. Build artifacts are retained on failure; only a successful
playback report establishes a verified package.

## Follow-up transport investigation

The supplied APK's `bz` interceptor applies the legacy Iron signature to V3
requests as well. Its packaged OkHttp transport identifies as `okhttp/4.12.0`.
Both are now preserved. This does not change registration's Windows platform.

The decrypted public domain configuration still lists `api.ossccaarr111.online`
as a backup. Another configured, reachable origin, `api.myychann.site`, returned
the same 60.8-second rejection in the diagnostic. Adding Iron alone and a valid
anonymous account session also returned that same rejection. Consequently no
account-login UI has been added.

The source's published player policy requires `com.tdm.manager` build 58.
The official TDM 2.2.2 APK has SHA-256
`a0f8ee9a8eebc2ecbabf304f95c475a94c82fb2b33653aa93c629b25896db994`.
Its `VideoPlayerActivity.z0/F0` carries source-issued cookies and its `sd.a.K`
adds per-resource metadata for `.css` playlists and `.js` segments. These headers
are now computed at the media network boundary, only for an already scoped
source session. Cross-origin requests still lose the cookie. Local diagnostic
requests with this metadata still received the notice. The follow-up packaged
Windows run also received the notice, so it is not confined to the diagnostic
host's transport. These changes do not yet fix playback.

The Windows verification driver now records each movie, episode, channel, and
download result independently. A failed first HLS movie no longer prevents
inspection of B2 or live channels. Download checks reject short replacement
playlists before accepting a remuxed sample.

An additional probe synthesizing X-TE player-state evidence was rejected by
automatic approval review. That probe was removed and is not part of the
application or the release workflow. No result from it is used as evidence.
The source API and CDN-side rejection logic were not found in the inspected
repository tree. The exact upstream rejection condition is still unverified.
Further diagnosis needs the API/CDN verification implementation or the matching
server-side rejection log. Client HTTP success alone cannot reveal which
server-side condition selected the replacement video.

This hotfix does not include the separate unfinished remote administration
work. Existing watch history, favorites, navigation, and AWR code are retained.
