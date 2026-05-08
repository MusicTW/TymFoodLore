package club.timehut.foodlore

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class TymFoodLorePlugin : JavaPlugin(), Listener {
    private val legacy = LegacyComponentSerializer.legacyAmpersand()
    private val miniMessage = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()
    private val itemsAdderFood = HashMap<String, FoodInfo>()
    private val effectNames = HashMap<String, String>()
    private val generatedEffectPrefixes = HashSet<String>()
    private val codexItemsAdderDiscoveries = HashMap<String, String>()

    private var pluginEnabled = false
    private var vanillaEnabled = false
    private var usePaperFoodData = false
    private var skipCustomModelData = false
    private var itemsAdderEnabled = false
    private var itemsAdderContentsPath = "plugins/ItemsAdder/contents"
    private var delayedItemsAdderRescanSeconds = 0
    private var periodicScanSeconds = 0
    private var displayPosition = "bottom"
    private var blankLineBefore = true
    private var headerFormat = ""
    private var titleFormat = ""
    private var nutritionFormat = ""
    private var saturationFormat = ""
    private var effectFormat = ""
    private var extraEffectFormat = ""
    private var noEffectText = "無"
    private var hiddenEffectText = "食用後觸發神祕狀態"
    private var unknownText = "未設定"
    private var hiddenEffectMaterials: Set<Material> = emptySet()
    private var codexEnabled = false
    private var codexCategory = "food"
    private var codexFlagPrefix = "codex.flag.food_"
    private var codexNotify = true
    private var periodicTaskId = -1
    private var itemsAdderBridge = ItemsAdderBridge.unavailable()

    override fun onEnable() {
        saveDefaultConfig()
        loadSettings()
        server.pluginManager.registerEvents(this, this)
        scheduleItemsAdderRescan()
        schedulePeriodicScan()
        runLater({ normalizeOnlinePlayers() }, 20L)
        logger.info("TymFoodLore 已啟用。ItemsAdder 食物資料: ${itemsAdderFood.size}")
    }

    override fun onDisable() {
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId)
            periodicTaskId = -1
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("status", ignoreCase = true)) {
            sender.sendMessage("TymFoodLore: ${if (pluginEnabled) "enabled" else "disabled"}")
            sender.sendMessage("ItemsAdder API: ${if (itemsAdderBridge.available()) "available" else "unavailable"}")
            sender.sendMessage("ItemsAdder food entries: ${itemsAdderFood.size}")
            sender.sendMessage("Codex food unlocks: ${if (codexEnabled) codexItemsAdderDiscoveries.size else "disabled"}")
            sender.sendMessage("Vanilla food: ${if (vanillaEnabled) "enabled" else "disabled"}")
            return true
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            reloadConfig()
            loadSettings()
            schedulePeriodicScan()
            normalizeOnlinePlayers()
            sender.sendMessage("TymFoodLore 已重新載入，並掃描線上玩家背包。ItemsAdder entries=${itemsAdderFood.size}")
            return true
        }

        if (args[0].equals("scan", ignoreCase = true)) {
            val changed = normalizeOnlinePlayers()
            sender.sendMessage("TymFoodLore 已掃描線上玩家背包，更新物品數: $changed")
            return true
        }

        sender.sendMessage("用法: /$label [status|reload|scan]")
        return true
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (config.getBoolean("normalization.run-on-join", true)) {
            runLater({ normalizePlayer(event.player) }, 20L)
        }
    }

    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        if (!config.getBoolean("normalization.run-on-pickup", true)) {
            return
        }
        if (event.entity is Player) {
            normalizeItem(event.item.itemStack)
        }
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        normalizeItem(event.itemDrop.itemStack)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!config.getBoolean("normalization.run-on-inventory-click", true)) {
            return
        }
        val player = event.whoClicked as? Player ?: return
        runLater({
            normalizePlayer(player)
            normalizeInventoryIfPlayerOwned(event.clickedInventory, player)
            normalizeInventoryIfPlayerOwned(event.view.topInventory, player)
        }, 1L)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (!config.getBoolean("normalization.run-on-inventory-click", true)) {
            return
        }
        val player = event.whoClicked as? Player ?: return
        runLater({ normalizePlayer(player) }, 1L)
    }

    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        if (!config.getBoolean("normalization.run-on-craft", true)) {
            return
        }
        val player = event.whoClicked as? Player ?: return
        runLater({ normalizePlayer(player) }, 1L)
    }

    @EventHandler
    fun onFurnaceExtract(event: FurnaceExtractEvent) {
        if (!config.getBoolean("normalization.run-on-furnace-extract", true)) {
            return
        }
        runLater({ normalizePlayer(event.player) }, 1L)
    }

    @EventHandler
    fun onHeld(event: PlayerItemHeldEvent) {
        runLater({ normalizePlayer(event.player) }, 1L)
    }

    @EventHandler
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (!codexEnabled) {
            return
        }
        val info = resolveFoodInfo(event.item) ?: return
        unlockCodexFood(event.player, info)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (!codexEnabled || !itemsAdderEnabled || event.hand != EquipmentSlot.HAND) {
            return
        }
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val item = event.item ?: return
        val info = resolveItemsAdderFoodInfo(item) ?: return
        if (!codexItemsAdderDiscoveries.containsKey(info.id.lowercase(Locale.ROOT))) {
            return
        }

        val player = event.player
        val beforeAmount = item.amount
        val beforeFoodLevel = player.foodLevel
        val beforeSaturation = player.saturation

        runLater({
            if (!player.isOnline) {
                return@runLater
            }

            val current = player.inventory.itemInMainHand
            val currentInfo = resolveItemsAdderFoodInfo(current)
            val itemChanged = current.type.isAir ||
                currentInfo?.id?.equals(info.id, ignoreCase = true) != true ||
                current.amount < beforeAmount
            val foodChanged = player.foodLevel > beforeFoodLevel ||
                player.saturation > beforeSaturation + 0.01f

            if (itemChanged || foodChanged) {
                unlockCodexFood(player, info)
            }
        }, 2L)
    }

    private fun loadSettings() {
        pluginEnabled = config.getBoolean("enabled", true)
        vanillaEnabled = config.getBoolean("vanilla.enabled", true)
        usePaperFoodData = config.getBoolean("vanilla.use-paper-data-components", true)
        skipCustomModelData = config.getBoolean("vanilla.skip-items-with-custom-model-data", true)
        itemsAdderEnabled = config.getBoolean("itemsadder.enabled", true)
        itemsAdderContentsPath = config.getString("itemsadder.contents-path", "plugins/ItemsAdder/contents")
            ?: "plugins/ItemsAdder/contents"
        delayedItemsAdderRescanSeconds = config.getInt("itemsadder.delayed-rescan-seconds", 8).coerceAtLeast(0)
        periodicScanSeconds = config.getInt("normalization.periodic-online-player-scan-seconds", 180).coerceAtLeast(0)
        displayPosition = config.getString("display.position", "bottom") ?: "bottom"
        blankLineBefore = config.getBoolean("display.blank-line-before", true)
        headerFormat = config.getString("display.header", "&8&m──────────────") ?: "&8&m──────────────"
        titleFormat = config.getString("display.title", "&6✦ 食物資訊") ?: "&6✦ 食物資訊"
        nutritionFormat = config.getString("display.nutrition-line", "&7飽食度 &f%nutrition% &8點")
            ?: "&7飽食度 &f%nutrition% &8點"
        saturationFormat = config.getString("display.saturation-line", "&7飽和度 &f%saturation% &8點")
            ?: "&7飽和度 &f%saturation% &8點"
        effectFormat = config.getString("display.effect-line", "&7特殊效果 &f%effect%")
            ?: "&7特殊效果 &f%effect%"
        extraEffectFormat = config.getString("display.extra-effect-line", "&7　　　　 &f%effect%")
            ?: "&7　　　　 &f%effect%"
        noEffectText = config.getString("display.no-effect", "無") ?: "無"
        hiddenEffectText = config.getString("display.hidden-effect", "食用後觸發神祕狀態") ?: "食用後觸發神祕狀態"
        unknownText = config.getString("display.unknown", "未設定") ?: "未設定"
        hiddenEffectMaterials = readHiddenEffectMaterials()
        loadCodexSettings()

        effectNames.clear()
        val effects = config.getConfigurationSection("language.effects")
        if (effects != null) {
            for (key in effects.getKeys(false)) {
                effectNames[key.lowercase(Locale.ROOT)] = effects.getString(key, key) ?: key
            }
        }
        rebuildGeneratedEffectPrefixes()

        itemsAdderBridge = ItemsAdderBridge.detect()
        loadItemsAdderFoodRegistry()
    }

    private fun loadCodexSettings() {
        codexEnabled = config.getBoolean("codex.enabled", false)
        codexCategory = config.getString("codex.category", "food") ?: "food"
        codexFlagPrefix = config.getString("codex.flag-prefix", "codex.flag.food_") ?: "codex.flag.food_"
        codexNotify = config.getBoolean("codex.notify", true)
        codexItemsAdderDiscoveries.clear()

        for (entry in config.getMapList("codex.itemsadder-food-discoveries")) {
            val item = entry["item"] ?: continue
            val discovery = entry["discovery"] ?: continue
            val itemId = item.toString().trim().lowercase(Locale.ROOT)
            val discoveryId = discovery.toString().trim().lowercase(Locale.ROOT)
            if (itemId.isNotBlank() && discoveryId.isNotBlank()) {
                codexItemsAdderDiscoveries[itemId] = discoveryId
            }
        }
    }

    private fun rebuildGeneratedEffectPrefixes() {
        generatedEffectPrefixes.clear()
        for (configuredName in effectNames.values) {
            addGeneratedEffectPrefix(configuredName)
        }
        for (type in Registry.EFFECT) {
            addGeneratedEffectPrefix(effectName(type.key.toString()))
        }
    }

    private fun addGeneratedEffectPrefix(text: String) {
        val compact = compactLoreText(text)
        if (compact.isNotBlank()) {
            generatedEffectPrefixes.add(compact)
        }
    }

    private fun loadItemsAdderFoodRegistry() {
        itemsAdderFood.clear()
        if (!itemsAdderEnabled) {
            return
        }

        val contents = resolveServerPath(itemsAdderContentsPath)
        if (!Files.isDirectory(contents)) {
            logger.warning("ItemsAdder contents path 不存在: $contents")
            return
        }

        try {
            Files.walk(contents).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { path ->
                        val lower = path.fileName.toString().lowercase(Locale.ROOT)
                        lower.endsWith(".yml") || lower.endsWith(".yaml")
                    }
                    .forEach(::loadItemsAdderFoodFile)
            }
        } catch (exception: Exception) {
            logger.warning("掃描 ItemsAdder contents 失敗: ${exception.message}")
        }
    }

    private fun loadItemsAdderFoodFile(path: Path) {
        val yaml = YamlConfiguration.loadConfiguration(path.toFile())
        val namespace = yaml.getString("info.namespace")
        val items = yaml.getConfigurationSection("items")
        if (namespace.isNullOrBlank() || items == null) {
            return
        }

        for (itemId in items.getKeys(false)) {
            val base = "items.$itemId"
            val info = readItemsAdderFoodInfo("$namespace:$itemId", yaml, base)
            if (info != null) {
                itemsAdderFood[info.id.lowercase(Locale.ROOT)] = info
            }
        }
    }

    private fun readItemsAdderFoodInfo(namespacedId: String, yaml: YamlConfiguration, base: String): FoodInfo? {
        var nutrition = readNumber(yaml, "$base.consumable.nutrition")
        var saturation = readNumber(yaml, "$base.consumable.saturation")

        val feedAmount = readNumber(yaml, "$base.events.eat.feed.amount")
        val feedSaturation = readNumber(yaml, "$base.events.eat.feed.saturation")
        if (feedAmount != null) {
            nutrition = feedAmount
            saturation = feedSaturation
        }

        if (nutrition == null && saturation == null) {
            return null
        }

        val effects = ArrayList<String>()
        val potionEffect = yaml.getConfigurationSection("$base.events.eat.potion_effect")
        if (potionEffect != null) {
            val type = potionEffect.getString("type", "")
            val amplifier = potionEffect.getInt("amplifier", 0)
            val duration = potionEffect.getInt("duration", -1)
            effects.add(formatPotionEffect(type, amplifier, duration, 1.0f))
        }

        return FoodInfo(namespacedId, nutrition, saturation, effects, "ItemsAdder")
    }

    private fun readNumber(yaml: YamlConfiguration, path: String): Double? {
        if (!yaml.contains(path)) {
            return null
        }
        val value = yaml.get(path)
        return when (value) {
            is Number -> value.toDouble()
            null -> null
            else -> value.toString().toDoubleOrNull()
        }
    }

    private fun resolveServerPath(configuredPath: String): Path {
        val path = Path.of(configuredPath)
        if (path.isAbsolute) {
            return path
        }
        val pluginsDir = dataFolder.parentFile
        val serverRoot = pluginsDir?.parentFile
        return (serverRoot?.toPath() ?: Path.of(".")).resolve(path).normalize()
    }

    private fun scheduleItemsAdderRescan() {
        if (itemsAdderEnabled && delayedItemsAdderRescanSeconds > 0) {
            runLater({
                loadItemsAdderFoodRegistry()
                normalizeOnlinePlayers()
                logger.info("ItemsAdder 食物資料重新掃描完成: ${itemsAdderFood.size}")
            }, delayedItemsAdderRescanSeconds * 20L)
        }
    }

    private fun schedulePeriodicScan() {
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId)
            periodicTaskId = -1
        }
        if (periodicScanSeconds <= 0) {
            return
        }
        periodicTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            this,
            Runnable { normalizeOnlinePlayers() },
            periodicScanSeconds * 20L,
            periodicScanSeconds * 20L,
        )
    }

    private fun normalizeOnlinePlayers(): Int {
        val changed = AtomicInteger()
        for (player in Bukkit.getOnlinePlayers()) {
            changed.addAndGet(normalizePlayer(player))
        }
        return changed.get()
    }

    private fun normalizePlayer(player: Player): Int {
        if (!pluginEnabled || !player.isOnline) {
            return 0
        }
        val inventory = player.inventory
        val changed = normalizeInventory(inventory)
        if (changed > 0) {
            player.updateInventory()
        }
        return changed
    }

    private fun normalizeInventoryIfPlayerOwned(inventory: Inventory?, player: Player) {
        if (inventory == null || inventory.type == InventoryType.CREATIVE) {
            return
        }
        val holder = inventory.holder as? Player
        if (holder != null && holder.uniqueId == player.uniqueId) {
            normalizeInventory(inventory)
        }
    }

    private fun normalizeInventory(inventory: Inventory?): Int {
        if (inventory == null) {
            return 0
        }
        var changed = 0
        val contents = inventory.contents
        for (slot in contents.indices) {
            val item = contents[slot]
            if (normalizeItem(item)) {
                inventory.setItem(slot, item)
                changed++
            }
        }
        return changed
    }

    private fun normalizeItem(item: ItemStack?): Boolean {
        if (!pluginEnabled || item == null || item.type.isAir || item.amount <= 0) {
            return false
        }

        val info = resolveFoodInfo(item) ?: return false
        val meta = item.itemMeta ?: return false
        val existing = meta.lore() ?: emptyList()
        val cleaned = removeManagedLore(existing)
        val foodLore = buildFoodLore(info)
        val next = ArrayList<Component>()

        val top = displayPosition.equals("top", ignoreCase = true)
        if (top) {
            next.addAll(foodLore)
            if (cleaned.isNotEmpty() && blankLineBefore) {
                next.add(Component.empty())
            }
            next.addAll(cleaned)
        } else {
            next.addAll(cleaned)
            if (next.isNotEmpty() && blankLineBefore) {
                next.add(Component.empty())
            }
            next.addAll(foodLore)
        }

        if (sameLore(existing, next)) {
            return false
        }

        meta.lore(next)
        item.itemMeta = meta
        return true
    }

    private fun unlockCodexFood(player: Player, info: FoodInfo) {
        if (info.source != "ItemsAdder") {
            return
        }
        val discovery = codexItemsAdderDiscoveries[info.id.lowercase(Locale.ROOT)] ?: return
        val flag = codexFlagPrefix + discovery
        if (hasCodexDiscovery(player, discovery, flag)) {
            return
        }

        val notify = if (codexNotify) " true" else " false"
        val codexCommand = Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            "codex unlock ${player.name} $codexCategory $discovery$notify",
        )
        val luckPermsCommand = Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            "lp user ${player.name} permission set $flag true",
        )
        logger.info(
            "Codex 食物解鎖請求: player=${player.name}, item=${info.id}, discovery=$discovery, " +
                "codexCommand=$codexCommand, luckPermsCommand=$luckPermsCommand",
        )
    }

    private fun hasCodexDiscovery(player: Player, discovery: String, fallbackFlag: String): Boolean {
        return try {
            val api = Class.forName("cx.ajneb97.api.CodexAPI")
            val method = api.getMethod(
                "hasDiscovery",
                Player::class.java,
                String::class.java,
                String::class.java,
            )
            method.invoke(null, player, codexCategory, discovery) as? Boolean ?: false
        } catch (_: ReflectiveOperationException) {
            hasExactPermissionFlag(player, fallbackFlag)
        } catch (_: LinkageError) {
            hasExactPermissionFlag(player, fallbackFlag)
        } catch (_: RuntimeException) {
            hasExactPermissionFlag(player, fallbackFlag)
        }
    }

    private fun hasExactPermissionFlag(player: Player, flag: String): Boolean {
        return player.effectivePermissions.any { attachment ->
            attachment.value && attachment.permission.equals(flag, ignoreCase = true)
        }
    }

    private fun resolveFoodInfo(item: ItemStack): FoodInfo? {
        val itemsAdderInfo = resolveItemsAdderFoodInfo(item)
        if (itemsAdderInfo != null) {
            return itemsAdderInfo
        }

        if (!vanillaEnabled || !usePaperFoodData) {
            return null
        }

        if (skipCustomModelData && hasCustomModelData(item)) {
            return null
        }

        val food = item.getData(DataComponentTypes.FOOD) ?: return null
        var effects = readConsumableEffects(item)
        if (shouldUseHiddenEffectText(item)) {
            effects = listOf(hiddenEffectText)
        }
        return FoodInfo(
            item.type.key.toString(),
            food.nutrition().toDouble(),
            food.saturation().toDouble(),
            effects,
            "vanilla",
        )
    }

    private fun resolveItemsAdderFoodInfo(item: ItemStack): FoodInfo? {
        if (!itemsAdderEnabled || !itemsAdderBridge.available()) {
            return null
        }

        val itemsAdderId = itemsAdderBridge.namespacedId(item)?.lowercase(Locale.ROOT) ?: return null
        return itemsAdderFood[itemsAdderId]
    }

    private fun readHiddenEffectMaterials(): Set<Material> {
        val materials = HashSet<Material>()
        for (configured in config.getStringList("vanilla.hidden-effect-materials")) {
            val material = Material.matchMaterial(configured)
            if (material == null) {
                logger.warning("未知的 hidden-effect-material: $configured")
                continue
            }
            materials.add(material)
        }
        return materials
    }

    private fun shouldUseHiddenEffectText(item: ItemStack): Boolean {
        if (!hiddenEffectMaterials.contains(item.type)) {
            return false
        }

        val stewEffects = item.getData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS)
        return stewEffects == null || stewEffects.effects().isNotEmpty()
    }

    @Suppress("DEPRECATION")
    private fun hasCustomModelData(item: ItemStack): Boolean {
        val meta: ItemMeta = item.itemMeta ?: return false
        return meta.hasCustomModelData()
    }

    private fun readConsumableEffects(item: ItemStack): List<String> {
        val consumable = item.getData(DataComponentTypes.CONSUMABLE) ?: return emptyList()
        val effects = ArrayList<String>()
        for (effect in consumable.consumeEffects()) {
            when (effect) {
                is ConsumeEffect.ApplyStatusEffects -> {
                    val probability = effect.probability()
                    for (potionEffect in effect.effects()) {
                        effects.add(formatPotionEffect(potionEffect, probability))
                    }
                }

                is ConsumeEffect.ClearAllStatusEffects -> {
                    effects.add(effectName("minecraft:clear_all_status_effects"))
                }

                is ConsumeEffect.RemoveStatusEffects -> {
                    effects.add(formatRemoveEffects(effect.removeEffects().resolve(Registry.EFFECT)))
                }

                is ConsumeEffect.TeleportRandomly -> {
                    effects.add(effectName("minecraft:teleport_randomly") + " " + formatNumber(effect.diameter().toDouble()) + "格")
                }
            }
        }
        return effects
    }

    private fun formatRemoveEffects(types: Collection<PotionEffectType>?): String {
        if (types.isNullOrEmpty()) {
            return effectName("minecraft:remove_status_effects")
        }
        val names = types.map { effectName(it.key.toString()) }
        return effectName("minecraft:remove_status_effects") + " " + names.joinToString("、")
    }

    private fun formatPotionEffect(effect: PotionEffect, probability: Float): String {
        return formatPotionEffect(effect.type.key.toString(), effect.amplifier, effect.duration, probability)
    }

    private fun formatPotionEffect(type: String?, amplifier: Int, durationTicks: Int, probability: Float): String {
        val name = effectName(normalizeEffectKey(type))
        val level = if (amplifier >= 0 && amplifier < ROMAN.size) ROMAN[amplifier] else (amplifier + 1).toString()
        val duration = if (durationTicks >= 0) " ${formatDuration(durationTicks)}" else ""
        val chance = if (probability < 0.999f) " ${formatNumber(probability * 100.0)}%" else ""
        return "$name $level$duration$chance"
    }

    private fun normalizeEffectKey(type: String?): String {
        if (type.isNullOrBlank()) {
            return "unknown"
        }
        val lower = type.lowercase(Locale.ROOT)
        return if (lower.contains(":")) lower else "minecraft:$lower"
    }

    private fun effectName(key: String): String {
        val normalized = normalizeEffectKey(key)
        return effectNames[normalized] ?: normalized.substring(normalized.indexOf(':') + 1).replace('_', ' ')
    }

    private fun formatDuration(ticks: Int): String {
        if (ticks < 0) {
            return unknownText
        }
        val seconds = ticks / 20.0
        return formatNumber(seconds) + "秒"
    }

    private fun formatNumber(value: Double): String {
        return NUMBER_FORMAT.format(value)
    }

    private fun buildFoodLore(info: FoodInfo): List<Component> {
        val lines = ArrayList<Component>()
        addFormattedLine(lines, headerFormat, info, null)
        addFormattedLine(lines, titleFormat, info, null)
        addFormattedLine(lines, nutritionFormat, info, null)
        addFormattedLine(lines, saturationFormat, info, null)

        val effects = if (info.effects.isEmpty()) listOf(noEffectText) else info.effects
        for (index in effects.indices) {
            addFormattedLine(lines, if (index == 0) effectFormat else extraEffectFormat, info, effects[index])
        }
        return lines
    }

    private fun addFormattedLine(lines: MutableList<Component>, format: String?, info: FoodInfo, effect: String?) {
        if (format.isNullOrBlank()) {
            return
        }
        val line = format
            .replace("%id%", info.id)
            .replace("%source%", info.source)
            .replace("%nutrition%", info.nutrition?.let(::formatNumber) ?: unknownText)
            .replace("%saturation%", info.saturation?.let(::formatNumber) ?: unknownText)
            .replace("%effect%", effect ?: "")
        lines.add(parseFormattedLine(line))
    }

    private fun parseFormattedLine(line: String): Component {
        if (line.contains("<") && line.contains(">")) {
            try {
                return withoutItalic(miniMessage.deserialize(line))
            } catch (_: RuntimeException) {
                return withoutItalic(legacy.deserialize(line))
            }
        }
        return withoutItalic(legacy.deserialize(line))
    }

    private fun withoutItalic(component: Component): Component {
        return component.decoration(TextDecoration.ITALIC, false)
    }

    private fun removeManagedLore(lore: List<Component>): List<Component> {
        val cleaned = ArrayList<Component>()
        for (component in lore) {
            val text = plain.serialize(component)
            if (isManagedLoreLine(text)) {
                continue
            }
            cleaned.add(component)
        }
        while (cleaned.isNotEmpty() && plain.serialize(cleaned[cleaned.size - 1]).isBlank()) {
            cleaned.removeAt(cleaned.size - 1)
        }
        return cleaned
    }

    private fun isManagedLoreLine(text: String): Boolean {
        val compact = compactLoreText(text)
        return compact.contains("食物資訊") ||
            compact.contains("食物屬性") ||
            compact.contains("食用屬性") ||
            compact.contains("飽食度") ||
            compact.contains("飽和度") ||
            compact.startsWith("飽食") ||
            compact.startsWith("飽和") ||
            compact.contains("特殊效果") ||
            compact.startsWith("效果") ||
            isGeneratedEffectLine(compact) ||
            compact.contains("────────")
    }

    private fun isGeneratedEffectLine(compact: String?): Boolean {
        if (compact.isNullOrBlank()) {
            return false
        }

        if (compact == compactLoreText(hiddenEffectText)) {
            return true
        }

        if (looksLikeGeneratedPotionEffect(compact)) {
            return true
        }

        for (effectPrefix in generatedEffectPrefixes) {
            if (
                compact.startsWith(effectPrefix) &&
                looksLikeGeneratedPotionEffect(compact.substring(effectPrefix.length))
            ) {
                return true
            }
        }
        return false
    }

    private fun looksLikeGeneratedPotionEffect(compact: String?): Boolean {
        if (compact.isNullOrBlank()) {
            return false
        }
        return GENERATED_POTION_EFFECT_WITH_DURATION.matcher(compact).matches() ||
            GENERATED_POTION_EFFECT_WITH_CHANCE.matcher(compact).matches() ||
            GENERATED_POTION_EFFECT_LEVEL_ONLY.matcher(compact).matches()
    }

    private fun compactLoreText(text: String?): String {
        return text?.replace(" ", "")?.replace("　", "") ?: ""
    }

    private fun sameLore(first: List<Component>, second: List<Component>): Boolean {
        return first == second
    }

    private fun runLater(task: () -> Unit, delayTicks: Long) {
        Bukkit.getScheduler().runTaskLater(this, Runnable { task() }, delayTicks)
    }

    private data class FoodInfo(
        val id: String,
        val nutrition: Double?,
        val saturation: Double?,
        val effects: List<String>,
        val source: String,
    )

    private class ItemsAdderBridge private constructor(
        private val byItemStack: Method?,
        private val getNamespacedId: Method?,
    ) {
        fun available(): Boolean {
            return byItemStack != null && getNamespacedId != null
        }

        fun namespacedId(item: ItemStack): String? {
            if (!available()) {
                return null
            }
            return try {
                val customStack = byItemStack!!.invoke(null, item) ?: return null
                getNamespacedId!!.invoke(customStack)?.toString()
            } catch (_: ReflectiveOperationException) {
                null
            }
        }

        companion object {
            fun detect(): ItemsAdderBridge {
                return try {
                    val customStack = Class.forName("dev.lone.itemsadder.api.CustomStack")
                    ItemsAdderBridge(
                        customStack.getMethod("byItemStack", ItemStack::class.java),
                        customStack.getMethod("getNamespacedID"),
                    )
                } catch (_: ReflectiveOperationException) {
                    unavailable()
                }
            }

            fun unavailable(): ItemsAdderBridge {
                return ItemsAdderBridge(null, null)
            }
        }
    }

    private companion object {
        val NUMBER_FORMAT = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US))
        val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
        const val ROMAN_PATTERN = "(?:X|IX|VIII|VII|VI|V|IV|III|II|I)"
        val GENERATED_POTION_EFFECT_WITH_DURATION: Pattern = Pattern.compile(
            ".+$ROMAN_PATTERN(?:\\d+(?:\\.\\d+)?秒|未設定)(?:\\d+(?:\\.\\d+)?%)?",
        )
        val GENERATED_POTION_EFFECT_WITH_CHANCE: Pattern = Pattern.compile(
            ".+$ROMAN_PATTERN\\d+(?:\\.\\d+)?%",
        )
        val GENERATED_POTION_EFFECT_LEVEL_ONLY: Pattern = Pattern.compile(
            ".+$ROMAN_PATTERN",
        )
    }
}
