# 更新紀錄

## 1.2.1 - 2026-05-08

### 修正
- ItemsAdder `events.eat` 食物可能不會觸發 Bukkit `PlayerItemConsumeEvent`，因此新增右鍵 IA 食物時的 Codex 解鎖 fallback。
- Codex 解鎖請求會寫入伺服器 log，方便確認食物 ID 與 discovery 對應是否有執行。

## 1.2.0 - 2026-05-08

### 變更
- 插件主程式由 Java 改寫為 Kotlin。
- Gradle 改用 Kotlin JVM plugin 建置。
- 發布 jar 內含 Kotlin runtime，伺服器不需要另外安裝 Kotlin 前置插件。
- README、release notes 改為繁體中文並補上支援插件連結。

## 1.1.0 - 2026-05-08

### 新增
- 新增 Codex 食物圖鑑整合，可將 ItemsAdder 食物對應到 Codex discovery。
- 玩家食用已對應的 ItemsAdder 食物時，會自動呼叫 Codex 解鎖。
- 解鎖後寫入 LuckPerms 權限旗標，避免重複觸發同一個圖鑑條目。
- `/tymfoodlore status` 新增 Codex 對應數量顯示。

### 變更
- `plugin.yml` 新增 Codex 與 LuckPerms soft depend。
- 補上 README、MIT License、CHANGELOG 與 GitHub Actions build workflow。

## 1.0.0 - 2026-05-08

### 新增
- 初版發布，用於替原版食物與 ItemsAdder 食物加入食物 lore。
- 自動顯示飽食度、飽和度與特殊效果。
- 支援撿起物品、合成、熔爐取出、背包點擊、背包拖曳、切換手持物品、玩家登入與週期掃描時整理物品。
- 新增 `/tymfoodlore status`、`/tymfoodlore reload`、`/tymfoodlore scan`。
