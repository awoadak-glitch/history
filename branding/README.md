# Application branding

## PALMA

The stable Oscar-hosted build now uses the product name **PALMA**. The mark combines a
cinema play symbol with a gold palm branch, on a dark burgundy/black field. It is intentionally
distinct from the former Oscar TV artwork while retaining a premium cinema identity.

- Generated master: 1254 × 1254; the reproducible Android production assets below are committed.
- Android launcher variants: mdpi through xxxhdpi, including round icons.
- Splash artwork: 843 × 624, matching the existing resource contract.
- Palette: black, burgundy and metallic gold.
- Generation prompt: “Create a premium cinematic app icon with a polished metallic-gold palm
  branch embracing a gold play triangle, centered on a deep black-to-burgundy background,
  elegant award-inspired identity, no words, no letters, square Android launcher composition.”
- `tooling/build_oscar_direct.py` changes only the existing resource entries and preserves
  `assets/base.apk` byte-for-byte.

## AWR World (legacy)

The user requested a new name/logo and a default night theme on 2026-09-19.

- Name: عالم AWR.
- Source tab: عالم المصادر.
- Palette: background `#101116`, surfaces `#1b1d25`, accent `#eec60a`, text `#f5f5f7`.
- `awr-logo.png`: generated with the built-in image-generation tool; production launcher master resized to 384px. The original generated image remains available in the conversation.
- Prompt: original geometric golden wolf head, play triangle in negative space, minimal symmetrical emblem, dark navy background, legible Android launcher safe area, no text.
- `tooling/brand_resources.py` updates existing resource names/IDs and all existing AppTheme configurations. It also updates the anime toolbar and card rounding; all anime content logic stays in the original DEX.
