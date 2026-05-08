# 更新紀錄

## 1.2.0 - 2026-05-08

### 變更
- 將插件主程式由 Java 改寫為 Kotlin。
- Gradle 改用 Kotlin JVM plugin 建置。
- 發布 jar 會打包 Kotlin runtime，伺服器不需要額外安裝 Kotlin 前置插件。
- GitHub README 與更新紀錄改為繁體中文。

### 驗證
- 確認 Kotlin 版 jar 內包含 `club.timehut.foodlore.TymFoodLorePlugin` 主類別。
- 確認 jar 內包含 Kotlin runtime。
- 確認 `plugin.yml` 版本為 `1.2.0`。

## 1.1.0 - 2026-05-08

### 新增
- 新增可選的 Codex 食物圖鑑整合，可將 ItemsAdder 食物對應到 Codex discovery。
- 新增 `codex.itemsadder-food-discoveries` 設定，用來指定 ItemsAdder 食物 ID 與 Codex 條目 ID。
- 玩家食用已對應的 ItemsAdder 食物時，會自動呼叫 Codex 解鎖。
- 解鎖後會寫入 LuckPerms 權限旗標，避免重複觸發同一個圖鑑條目。
- `/tymfoodlore status` 新增 `Codex food unlocks` 對應數量顯示。

### 變更
- `plugin.yml` 新增 Codex 與 LuckPerms soft depend。
- PowerShell 建置腳本移除本機硬編路徑，改成可公開 repo 使用的版本。
- 補上 README、MIT License、CHANGELOG 與 GitHub Actions build workflow，讓專案可公開開源。

## 1.0.0 - 2026-05-08

### 新增
- 初版發布，用於替原版食物與 ItemsAdder 食物加入食物 lore。
- 自動顯示飽食度、飽和度與特殊效果。
- 支援撿起物品、合成、熔爐取出、背包點擊、背包拖曳、切換手持物品、玩家登入與週期掃描時整理物品。
- 新增 `/tymfoodlore status`、`/tymfoodlore reload`、`/tymfoodlore scan`。
