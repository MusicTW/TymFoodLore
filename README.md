# TymFoodLore

TymFoodLore is a Paper plugin for TymHaven. It adds readable food lore to vanilla and ItemsAdder foods, including nutrition, saturation, and potion-style effects.

## Features

- Adds configurable lore to vanilla foods and ItemsAdder foods.
- Reads ItemsAdder food data from `plugins/ItemsAdder/contents`.
- Normalizes player inventory items after pickup, crafting, inventory movement, joins, held-item changes, and periodic scans.
- Optionally unlocks Codex food discoveries when mapped ItemsAdder foods are consumed.
- Writes LuckPerms flags for Codex unlock tracking.

## Commands

- `/tymfoodlore status` - Shows plugin state, ItemsAdder entry count, Codex mapping count, and vanilla-food state.
- `/tymfoodlore reload` - Reloads config and rescans online player inventories.
- `/tymfoodlore scan` - Rescans online player inventories.

Permission: `tymfoodlore.admin`

## Build

Requirements:

- Java 21
- PowerShell
- Internet access for the first Gradle download

Build on Windows:

```powershell
.\build.ps1
```

The compiled jar is created under `build/libs/`.

## Server Deployment

1. Stop or restart the server during deployment.
2. Copy the built jar into the server `plugins` folder.
3. Keep only one TymFoodLore jar active in `plugins`.
4. Start the server and run `/tymfoodlore status`.

## Optional Integrations

`plugin.yml` declares soft dependencies for:

- ItemsAdder
- Codex
- LuckPerms

Codex unlocks are controlled by the `codex:` section in `config.yml`. Only mapped ItemsAdder food IDs are unlocked.
