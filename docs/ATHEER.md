# أثير — Oscar as the host

The latest user instruction supersedes the AWR application-ID variant. The base is the owner's Oscar TV 1.1.4 APK (`com.drama.mp4`). Branding becomes **أثير / Atheer**, with an ink-blue night palette and a new gold orbit/play icon. Original Oscar catalogue, detail, filters, seasons, channels, and playback code remain in the host; no API emulator replaces them.

## Internal navigation

`Host.attach` augments the existing scrollable bottom bar with **عالم المصادر** at list index 2. Original tab entries and callbacks are retained. Selecting it opens the original AWR anime activity inside the same installed package/task. The AWR source tab becomes **التبويبات الأساسية** and returns to the host's existing MainActivity.

AWR is bundled as a private feature APK, not installed as a second app. Android AppComponentFactory creates its declared activities with an isolated class loader. The module retains its resource IDs, native libraries, original application setup, and approved Drama/MX/season-restoration code. Context adapters provide module resources and preferences; they do not forge package names, signing certificates, or integrity results. Its files are extracted to private storage, SHA-256 checked, and made read-only before loading. Minimum Android version is 9 (API 28).

Original AWR manifest contains four activity declarations with no corresponding class in its installed DEX set: ProxyBillingActivity, OurAppsActivity, StreamActivity, and NewsDetailsActivity. They are not silently invented. They are excluded from the new manifest. 117 concrete activity classes receive module-context/theme adapters. Shared third-party service/provider declarations are not blindly duplicated.

## Update policy

Three calls to Oscar's application-update endpoint are replaced with a local disabled-update response. AppUpdate's availability/mandatory getters are disabled, while content authentication and original native integrity checks remain unchanged. The module is based on the previously approved AWR payload and retains its prior update-removal work.

## Branding provenance

`branding/atheer-logo.png` is derived at launcher size from the built-in image generator output. Prompt: a single golden crescent orbit embracing an abstract play-shaped star, restrained premium streaming/anime identity, dark ink background, no typography or existing trademarks. Original generated image remains in the workspace.

## Verification limits

This is a new integration architecture. APK assembly/signature/DEX checks cannot validate real Android lifecycle, native execution, Firebase/AppCheck acceptance, every SDK component, or backend acceptance of the new signing certificate. No emulator/device is attached here. Do not call it fully working until a phone verifies startup, every main section, Source World entry/exit, episode playback, downloads, background/restore, and process recreation.

Same package as Oscar; the new private AWR signing key differs from the original Oscar certificate. It cannot update that installation in place. Do not delete installed apps/data to work around this. A separate test phone without that conflicting package is the trial target. Existing AWR is a separate package and remains installed; its stored watch history is not automatically migrated.

## Latest startup correction

The first candidate below failed on the user's phone. Its merged Firebase registration metadata was incorrect. Use `Atheer-startup-fix.apk` (`a07c19d5…`) and read `ATHEER-STARTUP-FIX.md` plus `artifacts/atheer-startup-check.json`. Host and module now use distinct metadata services with their own original registrars. The correction keeps the previous Atheer signing certificate.

## Superseded candidate and reproducible verification

Candidate SHA-256: `e22366464e6b7ba15dfde2985c496c82b2727b89b5b04afd4542d80435ad68b2` (57,941,787 bytes), source commit `a120bc919fb9dd5b610fd5b57ec2a4de39aeebe1`.

Run `tooling/build_atheer.py --help` for the required input paths. Use a complete Android API JAR and the retained private signing key; provide its password through `AWR_KEYSTORE_PASSWORD`, never in arguments or Git. The build independently re-decodes resources, verifies stable IDs, checks all original native libraries, verifies ZIP alignment and v1/v2/v3 signatures, and emits `artifacts/atheer-build.json`.

`java -cp 'tooling/cache/oscar-tools/*' tooling/VerifyAtheer.java OSCAR.apk ATHEER.apk build/atheer-sources.apk build/module-activities.txt` independently checks class presence, compares the host application's classes, rejects unexpected content/authentication edits, verifies no update call remains, and checks all embedded activity adapters for missing classes or final-method conflicts. Results are saved in `artifacts/atheer-structure-check.json`. These are structural checks, not a device test.
