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

## 可選整合

`plugin.yml` 宣告了以下 soft depend：

- ItemsAdder
- Codex
- LuckPerms

Codex 解鎖由 `config.yml` 內的 `codex:` 區塊控制。只有列在 `codex.itemsadder-food-discoveries` 的 ItemsAdder 食物 ID 會觸發圖鑑解鎖。

## 授權

MIT License。詳見 [LICENSE](LICENSE)。
