# TymFoodLore

TymFoodLore 是 TymHaven 使用的 Paper 插件，用來替原版食物與 ItemsAdder 自訂食物補上清楚的食物說明。它會顯示飽食度、飽和度與特殊效果，並可選擇把指定的 ItemsAdder 食物接到 Codex 食物圖鑑解鎖流程。

插件本體以 Kotlin 撰寫，發布 jar 會內含 Kotlin runtime，伺服器不需要另外安裝 Kotlin 前置插件。

## 功能

- 替原版食物與 ItemsAdder 食物加入可設定的 lore。
- 從 `plugins/ItemsAdder/contents` 讀取 ItemsAdder 食物資料。
- 在玩家撿起物品、合成、熔爐取出、背包移動、登入、切換手持物品與週期掃描時，自動整理食物 lore。
- 可在玩家食用指定 ItemsAdder 食物時，自動解鎖 Codex 食物圖鑑。
- 可寫入 LuckPerms 權限旗標，避免同一個 Codex 條目重複觸發。

## 指令

- `/tymfoodlore status` - 查看插件狀態、ItemsAdder 食物數量、Codex 對應數量與原版食物狀態。
- `/tymfoodlore reload` - 重新載入設定，並掃描線上玩家背包。
- `/tymfoodlore scan` - 掃描線上玩家背包。

權限：`tymfoodlore.admin`

## 建置

需求：

- Java 21
- PowerShell
- 第一次下載 Gradle 時需要網路連線

Windows 建置：

```powershell
.\build.ps1
```

編譯完成的 jar 會輸出到 `build/libs/`。

## 伺服器部署

1. 部署時先停止或重啟伺服器。
2. 將編譯完成的 jar 複製到伺服器 `plugins` 資料夾。
3. `plugins` 內只保留一個啟用中的 TymFoodLore jar。
4. 啟動伺服器後執行 `/tymfoodlore status` 確認狀態。

## 支援與相容插件

TymFoodLore 以 Paper 伺服器為目標，並可選擇整合下列插件：

- [Paper](https://papermc.io/) / [Paper Docs](https://docs.papermc.io/) - 目標伺服器平台與 API。
- [ItemsAdder](https://itemsadder.com/) / [ItemsAdder Wiki](https://itemsadder.devs.beer/) - 讀取自訂食物資料，替 ItemsAdder 食物補上 lore。
- [Codex | RPG Discoveries](https://www.spigotmc.org/resources/codex-rpg-discoveries-1-16-5-1-21-9.90371/) / [Codex Wiki](https://ajneb97.gitbook.io/codex) - 食用指定 ItemsAdder 食物時解鎖食物圖鑑 discovery。
- [LuckPerms](https://luckperms.net/) / [LuckPerms GitHub](https://github.com/LuckPerms/LuckPerms) - 寫入解鎖旗標，避免同一個 discovery 重複觸發。

`plugin.yml` 內對 ItemsAdder、Codex、LuckPerms 使用 soft depend，因此這些整合是可選功能。Codex 解鎖由 `config.yml` 內的 `codex:` 區塊控制，只有列在 `codex.itemsadder-food-discoveries` 的 ItemsAdder 食物 ID 會觸發圖鑑解鎖。

## 開發與產生方式

這個專案是由 OpenAI Codex 依照 TymHaven 伺服器需求協助產生、重構與整理的開源插件，也就是常說的 vibe coding 工作流。

實際流程不是單純要求 AI 一次產出完整插件，而是：

1. 先讀取 live server 的插件設定、Codex 圖鑑、ItemsAdder 食物資料與既有行為。
2. 由人決定要整合的玩法目標與風格，例如食物 lore、Codex 食物圖鑑與 LuckPerms 解鎖旗標。
3. 由 Codex 協助撰寫 Kotlin 程式、Gradle 設定、README、CHANGELOG 與 GitHub release 內容。
4. 每次修改後都以實際 build、YAML 解析、jar 內容檢查與 live server 部署檢查做驗證。

因此，本專案可以視為 AI-assisted / Codex-generated 的 vibe coding 作品；程式碼公開是為了讓使用者能檢查、修改與延伸，而不是把它包裝成手寫閉源插件。

## 授權

MIT License。詳見 [LICENSE](LICENSE)。
