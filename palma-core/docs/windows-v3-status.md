# Windows playback after OscarTV 1.1.6

Status: V3 registration, encrypted detail/resolve responses, an HLS playlist,
and real MPEG-TS media bytes succeeded against the source on 2026-10-22.
Windows 2.2.7 packaging and Windows decoding/download verification are pending.

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
resolve HTTP 200; no update requirement or guard rejection; HLS HTTP 200/206;
first media segment returned MPEG-TS bytes. Account login was not called.

## Validation and release gate

63 local web/desktop unit tests pass. They cover signatures, encrypted replies,
time correction, persistence, origin separation, application identity, link
mapping, guards, and session propagation. They do not replace Windows testing.

The branch workflow builds Windows 2.2.7 and runs `--self-test-v3` in the packaged
Electron app. It verifies movie HLS, movie B2, episode decoding and seeking,
a live channel, and a short HLS download remuxed and decoded with bundled FFmpeg.
Reports contain status, dimensions, durations and frame counts, never signed
URLs or credentials. Build artifacts are retained on failure; only a successful
playback report establishes a verified package.

This hotfix does not include the separate unfinished remote administration
work. Existing watch history, favorites, navigation, and AWR code are retained.
