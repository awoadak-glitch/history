# AWR World — internal source tabs, new signing key

Source commit: 530784a73062c25340e03663c63b686e2e238898
Input: AWR-World-Sources-season-restore.apk
Input SHA-256: 85aca2be533a5905801b6633c94939e5f32ea88df28be1124298f5edcfdabd51
APK: AWR-World-internal-tabs.apk
APK SHA-256: 82570927efa97baaff6ac2bcdc5326f0ac0008782b38aec3eb2d752d4c3c9a0b
New signing certificate SHA-256: 4d79b25db46e1cee747f297e77ea23a793607d047cf3957017504e325bf559c1

The user explicitly authorized a new signing key. This APK is signed and verified with v1/v2/v3 schemes. Its certificate differs from the input, so it is not an install-over update. Preserve the private signing backup for future compatible builds. Keys and passwords are never committed here.

This patch removes the visible HiTV tab, removes the external Oscar launcher and its error recovery button, removes Oscar TV branding from section settings, and keeps retry/navigation inside AWR Source World. Existing Drama/MX/download and season-restoration bytecode are preserved, together with all resources and 28 other DEX files. The new merge preserves 174 integration classes and replaces only WitcherTabs, OscarApi, OscarExperience and generated children.

14 local regression tests pass. No actual-device runtime test was performed. Source catalogue requests still return 403 Forbidden. The original Oscar APK, its native signing library and its private service identity were not transplanted into AWR. Native source UI exists, but source access/playback integration is not completed; do not claim this patch makes Oscar content work.

Rebuild by signing this classes29.dex into the exact input APK with tooling/patch_apk.py, the user's new PKCS12 key and --allow-new-signature. Supply the key password through AWR_KEYSTORE_PASSWORD. The script verifies payload preservation, alignment and signatures. See internal-sources-build.json and internal-sources-preservation.json.
