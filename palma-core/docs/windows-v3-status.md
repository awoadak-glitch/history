# Windows playback after OscarTV 1.1.6

Status: NOT READY FOR RELEASE. Windows 2.2.7 packaging succeeded, but all five
native Windows playback/download checks failed on 2026-10-22 and 2026-10-23. The source
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

69 local web/desktop unit tests pass. They cover signatures, encrypted replies,
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

An earlier probe synthesizing X-TE player-state evidence was rejected by
automatic approval review. That probe was removed, was not rerun, and is not
part of the application or the release workflow. No result from that blocked
probe is used as evidence. The later, separately authorized diagnostic below
reports a real Windows host and truthfully reports that TDM is absent.

## Wrong-clip follow-up

The user reproduced the replacement video in the experimental Windows build.
A fresh ordinary V3 session at `2026-10-23T02:27:33.181Z` again registered and
resolved successfully. Movie 4655 selected link ID 44898, and the decoded media
URL matched the source's URL while retaining its scoped cookie and user agent.
The CDN returned HTTP 200 with `text/css`, but its normalized HLS SHA-256 was
`ac540b598ddfee6bdf1a73d7f13c471a17de3abf95b3075db0c2726532f7ab07`, identical
to the 60.8-second rejection notice observed in Windows. The sanitized report
is `windows-v3-rejection.json`; its CDN edge request ID can correlate server logs.

The resolver previously rejected only the old notice hostname, so this valid
HLS response under the regular CDN origin reached the player. The resolver and
media proxy now reject this exact known notice playlist with
`SOURCE_SESSION_REQUIRED`. Four regression tests cover the captured HTTP 200
response, nested playlist rewriting, newline variants, and legitimate short
clips. Short duration alone is deliberately not used to reject media.

This corrects the misleading playback of the known rejection clip. It does
not restore the requested film or episode, identify the server rule that
selected the notice, or establish a successful Windows playback release.

Native Windows run `39906015665` verified runtime commit
`bb3341b3c9060b94325691c1b7cf5cc73a62fe53`. All 69 unit tests passed and packaging
succeeded. All five playback/download cases now stop during preparation with
the client's `403 SOURCE_SESSION_REQUIRED` instead of playing or saving the
known notice. This is a local classification of the CDN's HTTP 200 replacement,
not an upstream HTTP 403. Requested-content playback still fails the release
gate. See `windows-v3-notice-detection-verification.json` for the native report.

## Explicitly authorized Windows player-evidence diagnostic

After the owner explicitly authorized the bounded test on 2026-10-23, workflow
`39959748294` ran diagnostic commit
`ccee213244bad6f1476a58e01ad4e9a31d21eb84` on a native Windows runner. The test
creates its own in-memory installation key and source-issued session. It adds
request-bound X-TE evidence only to signed V3 calls. Its payload says
`pf: windows`, `i: 0`, `h: ""`, and `v: 0`: it does not claim an installed TDM
package, Android platform, package signature, or hardware attestation chain.
The AES-GCM format passes an independent Python cryptography reference vector.

At `2026-10-23T13:16:12.451Z`, challenge, registration, movie detail, and resolve
all returned HTTP 200. Movie 4655/link 44898 resolved with
`tdm_guard: {action: "install"}`. The test respected that explicit rejection,
stopped before any media request, and recorded
`403 PALMA_PLAYER_VERIFICATION_REQUIRED` locally. The source's resolve edge
request ID is `a4f103dabd52dfd8-LAX`. The sanitized record is
`windows-player-evidence.json`.

This establishes a concrete server-side requirement to install TDM for the
truthful Windows/TDM-absent request. It does not prove that this is the only
condition behind the older CDN replacement clip. The diagnostic workflow
finishing successfully means it captured the result; `candidatePlayable` is
false, and no successful film playback is claimed. The evidence injector is
test-only and is not packaged into Windows 2.2.7.

The next correction requires the source's device/player policy and CDN/session
implementation: authorize the Windows player with its own installation key,
issue its normal content-scoped playback session, and keep device revocation,
premium access, and content selection checks. Treating an Android package as
installed on Windows is not a valid Windows support policy. After the upstream
change, rerun this diagnostic and all five packaged Windows playback/download
checks before releasing. There is no evidence that a new account-login UI will
resolve this specific player-install requirement.

Search did not locate `tdm_guard` or `oscar-te-v1` in the connected code index;
most connected repositories are not indexed, so this alone is not conclusive.
Direct tree inspection of the available media-related repositories found no
source V3 PHP backend or player-policy implementation. The five connected
Vercel projects expose Vercel app domains, not the source API/CDN domains.
The source backend repository or hosting connection is still needed to make
and verify the server-side correction.

## Detailed APK trace and control-URI correction

The owner requested a detailed reverse-engineering pass of the supplied APK.
An offline Androguard 4.1.4 inventory now covers 157 methods, with selected
instruction-level evidence and a full Arabic report in
`oscar-1.1.6-playback-analysis-ar.md` and `oscar-1.1.6-playback-evidence.json`.
The extractor is `palma-core/tools/trace-oscar-playback.py`. Java decompiler
output was checked against Dalvik where it dropped control-flow branches.

The native `h32.L`, `j44.K`, and `g32.K` handle `oscartv-guard://` as a control
response before opening a player. The Windows `playbackSource` previously
swallowed its decoder error and could select an HTTP fallback in the same
response. `SourcePlaybackGuardError` now survives that fallback loop, including
encoded/nested guard links and silent/unknown guard actions. Three regression
tests failed before the correction and pass afterward. This is a separate
compatibility fix; it does not establish the cause of every CDN notice.

Native Windows run `40026937982` tested commit
`a218513f636bfdf962ed3e061fc6ad5a350702cf`: all 72 unit tests passed, and the
Windows 2.2.7 package built successfully. All five real playback/download cases
still failed with the local `SOURCE_SESSION_REQUIRED` classification of the
known HTTP 200 notice. The new report is `windows-guard-link-verification.json`.
No working playback release is claimed or published.

The trace also confirms that `aa2.a` excludes V3 from hot-token signing and
that `ga5.e` reads the actual Android player package, version, and certificate
for X-TE. Companion TDM entitlement is a separate signed subscription path;
it is not a locally generated media URL. No installed-player evidence or
subscription entitlement has been fabricated.

This hotfix does not include the separate unfinished remote administration
work. Existing watch history, favorites, navigation, and AWR code are retained.
