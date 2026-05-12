# TymFoodLore

TymFoodLore 是一個給 Paper 伺服器使用的食物 lore 插件。它會替原版食物與 ItemsAdder 自訂食物顯示飽食度、飽和度與食用效果，並可選擇把指定食物接到 Codex 食物圖鑑。

![Build](https://github.com/MusicTW/TymFoodLore/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/github/license/MusicTW/TymFoodLore)
![Release](https://img.shields.io/github/v/release/MusicTW/TymFoodLore)

## 功能

- 替原版食物與 ItemsAdder 食物自動補上食物資訊 lore。
- 從 `plugins/ItemsAdder/contents` 讀取 ItemsAdder 食物資料。
- 在玩家撿起、合成、熔爐取出、背包移動、登入、切換手持物品與定時掃描時整理食物 lore。
- 可在玩家食用指定 ItemsAdder 食物時解鎖 Codex discovery。
- 可寫入 LuckPerms 權限旗標，避免同一個圖鑑條目重複觸發。

## 需求

- [Paper](https://papermc.io/) 1.21.x / [Paper Docs](https://docs.papermc.io/)
- Java 21

可選整合：

- [ItemsAdder](https://itemsadder.com/) / [Wiki](https://itemsadder.devs.beer/)
- [Codex | RPG Discoveries](https://www.spigotmc.org/resources/codex-rpg-discoveries-1-16-5-1-21-9.90371/) / [Wiki](https://ajneb97.gitbook.io/codex)
- [LuckPerms](https://luckperms.net/) / [GitHub](https://github.com/LuckPerms/LuckPerms)

`plugin.yml` 對 ItemsAdder、Codex、LuckPerms 使用 soft depend；沒有安裝這些插件時，對應整合不會啟用。

## 下載與安裝

1. 從 [Releases](https://github.com/MusicTW/TymFoodLore/releases) 下載最新版 jar。
2. 放入伺服器 `plugins` 資料夾。
3. 確認 `plugins` 內只保留一個啟用中的 TymFoodLore jar。
4. 重啟伺服器。
5. 使用 `/tymfoodlore status` 檢查載入狀態。

## 指令

| 指令 | 說明 |
| --- | --- |
| `/tymfoodlore status` | 查看插件狀態、ItemsAdder 食物數量與 Codex 對應數量。 |
| `/tymfoodlore reload` | 重新載入設定並掃描線上玩家背包。 |
| `/tymfoodlore scan` | 掃描線上玩家背包。 |

權限：`tymfoodlore.admin`

## 設定

主要設定在 `config.yml`。

Codex 解鎖由 `codex:` 區塊控制。只有列在 `codex.itemsadder-food-discoveries` 的 ItemsAdder 食物 ID 會觸發圖鑑解鎖。

```yaml
codex:
  enabled: true
  category: food
  flag-prefix: codex.flag.food_
  notify: true
  itemsadder-food-discoveries:
    - item: iasurvival:tomato
      discovery: ia_iasurvival_tomato
```

## 建置

```powershell
.\build.ps1
```

輸出 jar 會在 `build/libs/`。

建置需求：

- Java 21
- PowerShell
- 第一次下載 Gradle 時需要網路

## 開發說明

本專案是 OpenAI Codex 協助產生與重構的 vibe coding 作品。需求、玩法整合方向與驗證依照 TymHaven 伺服器實際情境決定；程式碼公開是為了方便檢查、修改與延伸。

維護規則：任何 TymFoodLore 更新都要同步疊代到 GitHub `https://github.com/MusicTW/TymFoodLore.git`，包含 code、config、README、CHANGELOG、build 或 release 相關變更。

## 授權

MIT License。詳見 [LICENSE](LICENSE)。
