# AWR latest-input patch — no HiTV tab / Oscar diagnosis

This is a selective DEX patch, not an installable APK and not a completed Oscar API integration.

Input APK: AWR-World-Sources-season-restore.apk
Input SHA-256: 85aca2be533a5905801b6633c94939e5f32ea88df28be1124298f5edcfdabd51
Required update-signing certificate SHA-256: 3586142178e22cb6193aec74422ea8b04c7e8300117a680118a554aa7421ce3e

Changes: remove the visible HiTV tab; keep existing navigation IDs; offer the verified public Oscar launcher on catalogue failure. Oscar API currently returns 403 Forbidden and the original app signs requests. Authorized source access is still needed for in-app catalogue/playback.

The original APK's resources, manifest, native libraries and 28 other DEX files are preserved. 174 existing integration classes, including the season fix, Ui, OscarCatalog and OscarMedia, are preserved byte-for-byte after canonical DEX serialization. Only WitcherTabs, OscarApi, OscarExperience and their generated classes were replaced.

14 local regression tests passed. No phone test or successful live Oscar playback has been observed. The signing key for the supplied input is unavailable. Do not delete the installed app or substitute a new certificate to claim a compatible update.

To finish with the matching PKCS12 key, use the repository tooling/patch_apk.py with --input <approved.apk> --dex classes29.dex --output <signed.apk> --compiler <apksig.jar> --keystore <key.p12> --alias <alias>. Supply the password through AWR_KEYSTORE_PASSWORD. It validates signatures and rejects a mismatched certificate by default. Do not commit the key or its password.
