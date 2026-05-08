# Changelog

## 1.1.0 - 2026-05-08

### Added
- Added optional Codex food discovery integration for ItemsAdder foods.
- Added configurable `codex.itemsadder-food-discoveries` mappings.
- Added automatic Codex unlocks when a mapped ItemsAdder food is consumed.
- Added LuckPerms flag writes to prevent repeated unlock handling.
- Added `Codex food unlocks` count to `/tymfoodlore status`.

### Changed
- Declared optional soft dependencies for Codex and LuckPerms.
- Made the PowerShell build script portable by removing local machine fallback paths.

## 1.0.0 - 2026-05-08

### Added
- Initial release for adding food lore to vanilla and ItemsAdder foods.
- Added automatic nutrition, saturation, and effect display.
- Added inventory normalization hooks for pickup, crafting, furnace extraction, click, drag, held item changes, joins, and periodic scans.
- Added `/tymfoodlore status`, `/tymfoodlore reload`, and `/tymfoodlore scan`.
