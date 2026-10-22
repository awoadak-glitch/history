# Windows playback after OscarTV 1.1.6

Status: development draft, blocked by source registration. No working Windows
playback release has been produced from this change.

## Evidence from the supplied application and live requests

- Supplied application: `com.drama.mp4`, version name `1.1.6`, version code `18`.
- Existing Windows 2.2.6 catalog requests still return content. A movie detail
  response has `update_required: true` and the message
  `حدّث التطبيق لمتابعة المشاهدة`; its links do not establish a usable playback
  session. Sending version 18 as a query parameter or `X-App-Ver` did not clear
  this requirement in the prior comparison.
- The supplied Android code has separate account endpoints (`api/auth/*`) and
  device playback registration (`api/v3/devices/*`). Account sync/login is not
  evidence that a Windows playback device has been accepted.
- The new Windows transport was exercised against the existing source origin
  `https://api.ossccaarr111.online/` on 2026-10-22. The challenge request returned
  HTTP 200. Registering a freshly generated P-256 public key with platform
  `windows` returned HTTP 403 and `attestation failed`.
- This refusal does not isolate the platform field as its cause: that request
  had no accepted application-attestation proof. It establishes failure to
  satisfy registration, not that changing the platform label would fix it or
  that the server necessarily needs a Windows-specific endpoint.
- The APK obtains `device_secret` from a successful registration response and
  stores it for subsequent requests. It is not a fixed playback credential in
  the APK that can be copied into a standalone Windows client.
- Consequently no authorized V3 detail, resolve, or real video request could
  follow that registration. A changed hostname or fabricated Android proof was
  not used to work around the refusal.

## Implemented in this draft

The desktop resolver uses V3 content details and resolves a selected link ID
through `api/v3/resolve.php` when the V3 response does not already contain a
link. Movie, series episode, anime episode, anime film, channel, wrestling and
match-highlight identifiers are kept distinct. Watch and download use the same
authorized media/session pipeline.

Each installation creates its own P-256 private key and random device ID. The
Windows main process stores registration credentials through Electron
`safeStorage`. Records are separated and validated by API origin. Keys and
source tokens are not sent to the renderer, committed, or logged.

V3 requests sign the method, encoded path, query hash, timestamp, random nonce,
and body hash. Encrypted responses are verified using AES-256-GCM and the
request-bound response key. Clock mismatch has one bounded retry. Registration
refusal has a cooldown, retains the same identity, and does not fall back to
legacy playback URLs. A token-renewal method is available for the source's
`api/v3/devices/token.php` contract.

Legacy `update_required` responses and V3 registration refusal now have distinct
errors. They are not reported as one failed playback server that a different
quality or server selection can necessarily fix. Source player-verification
requirements and premium flags are preserved.

## Remaining prerequisite

The source operator must support registration of this Windows client or provide
a documented Windows attestation/provisioning integration for its generated
public key. The transport exposes `provideRegistrationProof` for that supported
integration; no proof provider is configured in the desktop build. Android
binary identity or a mobile device's private credentials are not substituted.

After that integration is available, verify real movie and episode playback,
HLS segments, channels, and downloads on Windows, then package a new release.
Do not publish this draft as a tested 2.2.7 playback fix.

## Validation

The complete local web/desktop unit suite passed: 62 tests, including 13 new
tests for the V3 transport, playback mapping, and protected storage. The desktop
main module also passed the Node syntax check.

Local tests cover independent request-signature verification, encrypted-response
authentication, time correction, stable device identity, encrypted persistence,
origin separation, registration refusal, content/link mapping, and passing
source-issued cookies through the playback/download resolver. These tests use
synthetic source fixtures and do not prove that the live source accepts Windows.
Native Windows GUI playback and Windows DPAPI were not exercised here; the
storage integration was tested using an injected encryption adapter.
