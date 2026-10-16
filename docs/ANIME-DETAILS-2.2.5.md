# Anime details in PALMA VISION 2.2.5

The shared client in `palma-web` now has a dedicated Witcher/AWR anime detail
page. The Windows package copies these same files during its existing build.

- Hero, synopsis, alternate names, trailer, studios, source material, episode
  count and duration, age rating, season, start/end dates, and weekly broadcast
  day are shown when supplied by AWR.
- AWR follower ratings and MyAnimeList scores/ranks remain distinct.
- Characters are ordered with the main cast first. Their profiles contain real
  portraits, voice actors, and supplied performance languages. Biographies are
  collapsed to avoid unsolicited story spoilers.
- Character pages load 24 records at a time with a retryable "show more" action.
  Records are deduplicated by identity. Related works retain the source IDs.
- `nextEpTimeInSec` is an absolute Unix timestamp, not a duration. A one-second
  countdown uses the device clock and stops on navigation. Completed titles,
  missing schedules and elapsed announcements have explicit separate states.
  A source announcement is approximate; it does not promise subtitle availability.
- The watch action opens the existing episode list and server/player flow.

## Source contract

Reference: https://awr-stream-web.vercel.app/

The reference anime page already joins `anime_list/{id}/characters` with
`characters_list/{characterId}`. Voice actors are nested at `data.voices` in those
records. The previous helper read only the first 16 character relations; Naruto's
main character ID 17 was consequently absent from that response.

A narrowly scoped public route was added in the owner's `AWR_STREAM_WEB` repo:
`fusion-overrides/app/api/anime/[id]/extras/route.ts`. The bootstrap overlay is
required because this repository materializes its Next.js source at build time.
The route reads all relation pages, sorts the main cast first, hydrates only the
requested 24 records with concurrency eight, and returns compact public metadata:
`{characters, related, total, page, nextPage}`. `GET ?page=1` advances the page.
Credentials and raw Firestore paths are excluded from the response. The route
validates IDs/pages and uses the source's existing server clients and caches.

AWR production commit: `587dc2921f0ac2995f1e79c4bf01da555293dc24`.
Live checks after deployment returned 268 Naruto character relations, the four
main characters on page zero, and 31 source voice-actor entries for Naruto. Page
one returned another 24 records. Eight related titles resolved by actual MAL IDs.
The scheduled title `Hyouken no Majutsushi ga Sekai wo Suberu II` returned
`nextEpTimeInSec: 1792689235.476` at the time of inspection.

## Verification

42 shared-client tests and seven desktop tests passed locally. The release
workflow also exercises the packaged Windows application with live API data:
metadata and loaded poster; Naruto's character and voice portraits; character
pagination; a ticking source timestamp; navigation timer cleanup; and the watch
button through episode 001, server selection and decoded video. Existing movie,
series, live-channel, native-fullscreen and download checks remain release gates.
The workflow uploads its JSON report and screenshots before publishing a ZIP;
see the release's verification evidence for the packaged result.

The packaged 2.2.5 Windows check passed in Actions run 38842693128. See
`palma-windows-2.2.5-verification.json`. Screenshot review found a broken upstream
cover URL and inconsistent LTR title alignment; 2.2.6 keeps the same features and
adds the anime poster fallback plus right-aligned titles in the Arabic layout.

The final 2.2.6 package passed all 49 unit tests and the packaged live-source
verification in Actions run **38843220956** on 2026-10-16. The reviewed screenshot
now shows Naruto's actual poster as the failed-cover fallback and the title beside
the poster. `palma-windows-2.2.6-verification.json` records 268 character relations,
31 Naruto voice entries, pagination from 24 to 48 characters, 220 episodes, the
source countdown advancing by one second, and successful Naruto PD1080p decoding.
PALMA series, B2 movie, live channel, match routing, actor works, HLS download, and
native fullscreen checks also passed.

Final code commit: `d23bca89fc2a11532153a1de968b972f913e3c98`.
Release: https://github.com/awoadak-glitch/history/releases/tag/palma-windows-v2.2.6
ZIP: `PALMA-VISION-Windows-2.2.6.zip`, 182281112 bytes.
SHA256: `9ce477e11b651fe863082e6b2aaa8a37010315d069e20f6fa790bb005d30ef60`.
The released file's GitHub asset state is `uploaded`; its report and screenshots
are attached to the same release. These are sampled source checks, not a guarantee
for every title or third-party server.
