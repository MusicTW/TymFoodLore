package club.timehut.foodlore;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.SuspiciousStewEffects;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TymFoodLorePlugin extends JavaPlugin implements Listener {
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final String ROMAN_PATTERN = "(?:X|IX|VIII|VII|VI|V|IV|III|II|I)";
    private static final Pattern GENERATED_POTION_EFFECT_WITH_DURATION = Pattern.compile(
            ".+" + ROMAN_PATTERN + "(?:\\d+(?:\\.\\d+)?秒|未設定)(?:\\d+(?:\\.\\d+)?%)?");
    private static final Pattern GENERATED_POTION_EFFECT_WITH_CHANCE = Pattern.compile(
            ".+" + ROMAN_PATTERN + "\\d+(?:\\.\\d+)?%");
    private static final Pattern GENERATED_POTION_EFFECT_LEVEL_ONLY = Pattern.compile(
            ".+" + ROMAN_PATTERN);

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
    private final Map<String, FoodInfo> itemsAdderFood = new HashMap<>();
    private final Map<String, String> effectNames = new HashMap<>();
    private final Set<String> generatedEffectPrefixes = new HashSet<>();

    private boolean enabled;
    private boolean vanillaEnabled;
    private boolean usePaperFoodData;
    private boolean skipCustomModelData;
    private boolean itemsAdderEnabled;
    private String itemsAdderContentsPath;
    private int delayedItemsAdderRescanSeconds;
    private int periodicScanSeconds;
    private String displayPosition;
    private boolean blankLineBefore;
    private String headerFormat;
    private String titleFormat;
    private String nutritionFormat;
    private String saturationFormat;
    private String effectFormat;
    private String extraEffectFormat;
    private String noEffectText;
    private String hiddenEffectText;
    private String unknownText;
    private Set<Material> hiddenEffectMaterials = Set.of();
    private boolean codexEnabled;
    private String codexCategory;
    private String codexFlagPrefix;
    private boolean codexNotify;
    private final Map<String, String> codexItemsAdderDiscoveries = new HashMap<>();
    private int periodicTaskId = -1;

    private ItemsAdderBridge itemsAdderBridge = ItemsAdderBridge.unavailable();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        scheduleItemsAdderRescan();
        schedulePeriodicScan();
        runLater(this::normalizeOnlinePlayers, 20L);
        getLogger().info("TymFoodLore 已啟用。ItemsAdder 食物資料: " + itemsAdderFood.size());
    }

    @Override
    public void onDisable() {
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId);
            periodicTaskId = -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("TymFoodLore: " + (enabled ? "enabled" : "disabled"));
            sender.sendMessage("ItemsAdder API: " + (itemsAdderBridge.available() ? "available" : "unavailable"));
            sender.sendMessage("ItemsAdder food entries: " + itemsAdderFood.size());
            sender.sendMessage("Codex food unlocks: " + (codexEnabled ? codexItemsAdderDiscoveries.size() : "disabled"));
            sender.sendMessage("Vanilla food: " + (vanillaEnabled ? "enabled" : "disabled"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            schedulePeriodicScan();
            normalizeOnlinePlayers();
            sender.sendMessage("TymFoodLore 已重新載入，並掃描線上玩家背包。ItemsAdder entries=" + itemsAdderFood.size());
            return true;
        }

        if (args[0].equalsIgnoreCase("scan")) {
            int changed = normalizeOnlinePlayers();
            sender.sendMessage("TymFoodLore 已掃描線上玩家背包，更新物品數: " + changed);
            return true;
        }

        sender.sendMessage("用法: /" + label + " [status|reload|scan]");
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("normalization.run-on-join", true)) {
            runLater(() -> normalizePlayer(event.getPlayer()), 20L);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!getConfig().getBoolean("normalization.run-on-pickup", true)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            normalizeItem(event.getItem().getItemStack());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        normalizeItem(event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!getConfig().getBoolean("normalization.run-on-inventory-click", true)) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            runLater(() -> {
                normalizePlayer(player);
                normalizeInventoryIfPlayerOwned(event.getClickedInventory(), player);
                normalizeInventoryIfPlayerOwned(event.getView().getTopInventory(), player);
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!getConfig().getBoolean("normalization.run-on-inventory-click", true)) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            runLater(() -> normalizePlayer(player), 1L);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!getConfig().getBoolean("normalization.run-on-craft", true)) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            runLater(() -> normalizePlayer(player), 1L);
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!getConfig().getBoolean("normalization.run-on-furnace-extract", true)) {
            return;
        }
        runLater(() -> normalizePlayer(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        runLater(() -> normalizePlayer(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!codexEnabled) {
            return;
        }
        FoodInfo info = resolveFoodInfo(event.getItem());
        if (info != null) {
            unlockCodexFood(event.getPlayer(), info);
        }
    }

    private void loadSettings() {
        enabled = getConfig().getBoolean("enabled", true);
        vanillaEnabled = getConfig().getBoolean("vanilla.enabled", true);
        usePaperFoodData = getConfig().getBoolean("vanilla.use-paper-data-components", true);
        skipCustomModelData = getConfig().getBoolean("vanilla.skip-items-with-custom-model-data", true);
        itemsAdderEnabled = getConfig().getBoolean("itemsadder.enabled", true);
        itemsAdderContentsPath = getConfig().getString("itemsadder.contents-path", "plugins/ItemsAdder/contents");
        delayedItemsAdderRescanSeconds = Math.max(0, getConfig().getInt("itemsadder.delayed-rescan-seconds", 8));
        periodicScanSeconds = Math.max(0, getConfig().getInt("normalization.periodic-online-player-scan-seconds", 180));
        displayPosition = getConfig().getString("display.position", "bottom");
        blankLineBefore = getConfig().getBoolean("display.blank-line-before", true);
        headerFormat = getConfig().getString("display.header", "&8&m──────────────");
        titleFormat = getConfig().getString("display.title", "&6✦ 食物資訊");
        nutritionFormat = getConfig().getString("display.nutrition-line", "&7飽食度 &f%nutrition% &8點");
        saturationFormat = getConfig().getString("display.saturation-line", "&7飽和度 &f%saturation% &8點");
        effectFormat = getConfig().getString("display.effect-line", "&7特殊效果 &f%effect%");
        extraEffectFormat = getConfig().getString("display.extra-effect-line", "&7　　　　 &f%effect%");
        noEffectText = getConfig().getString("display.no-effect", "無");
        hiddenEffectText = getConfig().getString("display.hidden-effect", "食用後觸發神祕狀態");
        unknownText = getConfig().getString("display.unknown", "未設定");
        hiddenEffectMaterials = readHiddenEffectMaterials();
        loadCodexSettings();

        effectNames.clear();
        ConfigurationSection effects = getConfig().getConfigurationSection("language.effects");
        if (effects != null) {
            for (String key : effects.getKeys(false)) {
                effectNames.put(key.toLowerCase(Locale.ROOT), effects.getString(key, key));
            }
        }
        rebuildGeneratedEffectPrefixes();

        itemsAdderBridge = ItemsAdderBridge.detect();
        loadItemsAdderFoodRegistry();
    }

    private void loadCodexSettings() {
        codexEnabled = getConfig().getBoolean("codex.enabled", false);
        codexCategory = getConfig().getString("codex.category", "food");
        codexFlagPrefix = getConfig().getString("codex.flag-prefix", "codex.flag.food_");
        codexNotify = getConfig().getBoolean("codex.notify", true);
        codexItemsAdderDiscoveries.clear();

        for (Map<?, ?> entry : getConfig().getMapList("codex.itemsadder-food-discoveries")) {
            Object item = entry.get("item");
            Object discovery = entry.get("discovery");
            if (item == null || discovery == null) {
                continue;
            }
            String itemId = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
            String discoveryId = String.valueOf(discovery).trim().toLowerCase(Locale.ROOT);
            if (!itemId.isBlank() && !discoveryId.isBlank()) {
                codexItemsAdderDiscoveries.put(itemId, discoveryId);
            }
        }
    }

    private void rebuildGeneratedEffectPrefixes() {
        generatedEffectPrefixes.clear();
        for (String configuredName : effectNames.values()) {
            addGeneratedEffectPrefix(configuredName);
        }
        for (PotionEffectType type : Registry.EFFECT) {
            addGeneratedEffectPrefix(effectName(type.getKey().toString()));
        }
    }

    private void addGeneratedEffectPrefix(String text) {
        String compact = compactLoreText(text);
        if (!compact.isBlank()) {
            generatedEffectPrefixes.add(compact);
        }
    }

    private void loadItemsAdderFoodRegistry() {
        itemsAdderFood.clear();
        if (!itemsAdderEnabled) {
            return;
        }

        Path contents = resolveServerPath(itemsAdderContentsPath);
        if (!Files.isDirectory(contents)) {
            getLogger().warning("ItemsAdder contents path 不存在: " + contents);
            return;
        }

        try (Stream<Path> paths = Files.walk(contents)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return lower.endsWith(".yml") || lower.endsWith(".yaml");
                    })
                    .forEach(this::loadItemsAdderFoodFile);
        } catch (IOException exception) {
            getLogger().warning("掃描 ItemsAdder contents 失敗: " + exception.getMessage());
        }
    }

    private void loadItemsAdderFoodFile(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        String namespace = yaml.getString("info.namespace");
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (namespace == null || namespace.isBlank() || items == null) {
            return;
        }

        for (String itemId : items.getKeys(false)) {
            String base = "items." + itemId;
            FoodInfo info = readItemsAdderFoodInfo(namespace + ":" + itemId, yaml, base);
            if (info != null) {
                itemsAdderFood.put(info.id(), info);
            }
        }
    }

    private FoodInfo readItemsAdderFoodInfo(String namespacedId, YamlConfiguration yaml, String base) {
        Double nutrition = readNumber(yaml, base + ".consumable.nutrition");
        Double saturation = readNumber(yaml, base + ".consumable.saturation");

        Double feedAmount = readNumber(yaml, base + ".events.eat.feed.amount");
        Double feedSaturation = readNumber(yaml, base + ".events.eat.feed.saturation");
        if (feedAmount != null) {
            nutrition = feedAmount;
            saturation = feedSaturation;
        }

        if (nutrition == null && saturation == null) {
            return null;
        }

        List<String> effects = new ArrayList<>();
        ConfigurationSection potionEffect = yaml.getConfigurationSection(base + ".events.eat.potion_effect");
        if (potionEffect != null) {
            String type = potionEffect.getString("type", "");
            int amplifier = potionEffect.getInt("amplifier", 0);
            int duration = potionEffect.getInt("duration", -1);
            effects.add(formatPotionEffect(type, amplifier, duration, 1.0f));
        }

        return new FoodInfo(namespacedId, nutrition, saturation, effects, "ItemsAdder");
    }

    private Double readNumber(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) {
            return null;
        }
        Object value = yaml.get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Path resolveServerPath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        File pluginsDir = getDataFolder().getParentFile();
        File serverRoot = pluginsDir == null ? getDataFolder() : pluginsDir.getParentFile();
        return (serverRoot == null ? Path.of(".") : serverRoot.toPath()).resolve(path).normalize();
    }

    private void scheduleItemsAdderRescan() {
        if (itemsAdderEnabled && delayedItemsAdderRescanSeconds > 0) {
            runLater(() -> {
                loadItemsAdderFoodRegistry();
                normalizeOnlinePlayers();
                getLogger().info("ItemsAdder 食物資料重新掃描完成: " + itemsAdderFood.size());
            }, delayedItemsAdderRescanSeconds * 20L);
        }
    }

    private void schedulePeriodicScan() {
        if (periodicTaskId != -1) {
            Bukkit.getScheduler().cancelTask(periodicTaskId);
            periodicTaskId = -1;
        }
        if (periodicScanSeconds <= 0) {
            return;
        }
        periodicTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::normalizeOnlinePlayers,
                periodicScanSeconds * 20L, periodicScanSeconds * 20L);
    }

    private int normalizeOnlinePlayers() {
        AtomicInteger changed = new AtomicInteger();
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed.addAndGet(normalizePlayer(player));
        }
        return changed.get();
    }

    private int normalizePlayer(Player player) {
        if (!enabled || !player.isOnline()) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int changed = normalizeInventory(inventory);
        if (changed > 0) {
            player.updateInventory();
        }
        return changed;
    }

    private void normalizeInventoryIfPlayerOwned(Inventory inventory, Player player) {
        if (inventory == null || inventory.getType() == InventoryType.CREATIVE) {
            return;
        }
        if (inventory.getHolder() instanceof Player holder && holder.getUniqueId().equals(player.getUniqueId())) {
            normalizeInventory(inventory);
        }
    }

    private int normalizeInventory(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int changed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (normalizeItem(item)) {
                inventory.setItem(slot, item);
                changed++;
            }
        }
        return changed;
    }

    private boolean normalizeItem(ItemStack item) {
        if (!enabled || item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }

        FoodInfo info = resolveFoodInfo(item);
        if (info == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        List<Component> existing = meta.lore();
        List<Component> cleaned = removeManagedLore(existing == null ? List.of() : existing);
        List<Component> foodLore = buildFoodLore(info);
        List<Component> next = new ArrayList<>();

        boolean top = displayPosition.equalsIgnoreCase("top");
        if (top) {
            next.addAll(foodLore);
            if (!cleaned.isEmpty() && blankLineBefore) {
                next.add(Component.empty());
            }
            next.addAll(cleaned);
        } else {
            next.addAll(cleaned);
            if (!next.isEmpty() && blankLineBefore) {
                next.add(Component.empty());
            }
            next.addAll(foodLore);
        }

        if (sameLore(existing == null ? List.of() : existing, next)) {
            return false;
        }

        meta.lore(next);
        item.setItemMeta(meta);
        return true;
    }

    private void unlockCodexFood(Player player, FoodInfo info) {
        if (!"ItemsAdder".equals(info.source())) {
            return;
        }
        String discovery = codexItemsAdderDiscoveries.get(info.id().toLowerCase(Locale.ROOT));
        if (discovery == null) {
            return;
        }

        String flag = codexFlagPrefix + discovery;
        if (player.hasPermission(flag)) {
            return;
        }

        String notify = codexNotify ? " true" : " false";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "codex unlock " + player.getName() + " " + codexCategory + " " + discovery + notify);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + player.getName() + " permission set " + flag + " true");
    }

    private FoodInfo resolveFoodInfo(ItemStack item) {
        if (itemsAdderEnabled && itemsAdderBridge.available()) {
            String itemsAdderId = itemsAdderBridge.namespacedId(item);
            if (itemsAdderId != null) {
                FoodInfo info = itemsAdderFood.get(itemsAdderId);
                if (info != null) {
                    return info;
                }
                return null;
            }
        }

        if (!vanillaEnabled || !usePaperFoodData) {
            return null;
        }

        if (skipCustomModelData && hasCustomModelData(item)) {
            return null;
        }

        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        if (food == null) {
            return null;
        }

        List<String> effects = readConsumableEffects(item);
        if (shouldUseHiddenEffectText(item)) {
            effects = List.of(hiddenEffectText);
        }
        return new FoodInfo(item.getType().getKey().toString(), (double) food.nutrition(), (double) food.saturation(), effects, "vanilla");
    }

    private Set<Material> readHiddenEffectMaterials() {
        Set<Material> materials = new HashSet<>();
        for (String configured : getConfig().getStringList("vanilla.hidden-effect-materials")) {
            Material material = Material.matchMaterial(configured);
            if (material == null) {
                getLogger().warning("未知的 hidden-effect-material: " + configured);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    private boolean shouldUseHiddenEffectText(ItemStack item) {
        if (!hiddenEffectMaterials.contains(item.getType())) {
            return false;
        }

        SuspiciousStewEffects stewEffects = item.getData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
        return stewEffects == null || !stewEffects.effects().isEmpty();
    }

    private boolean hasCustomModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData();
    }

    private List<String> readConsumableEffects(ItemStack item) {
        Consumable consumable = item.getData(DataComponentTypes.CONSUMABLE);
        if (consumable == null) {
            return List.of();
        }

        List<String> effects = new ArrayList<>();
        for (ConsumeEffect effect : consumable.consumeEffects()) {
            if (effect instanceof ConsumeEffect.ApplyStatusEffects apply) {
                float probability = apply.probability();
                for (PotionEffect potionEffect : apply.effects()) {
                    effects.add(formatPotionEffect(potionEffect, probability));
                }
            } else if (effect instanceof ConsumeEffect.ClearAllStatusEffects) {
                effects.add(effectName("minecraft:clear_all_status_effects"));
            } else if (effect instanceof ConsumeEffect.RemoveStatusEffects remove) {
                effects.add(formatRemoveEffects(remove.removeEffects().resolve(Registry.EFFECT)));
            } else if (effect instanceof ConsumeEffect.TeleportRandomly teleport) {
                effects.add(effectName("minecraft:teleport_randomly") + " " + formatNumber(teleport.diameter()) + "格");
            }
        }
        return effects;
    }

    private String formatRemoveEffects(Collection<PotionEffectType> types) {
        if (types == null || types.isEmpty()) {
            return effectName("minecraft:remove_status_effects");
        }
        List<String> names = new ArrayList<>();
        for (PotionEffectType type : types) {
            names.add(effectName(type.getKey().toString()));
        }
        return effectName("minecraft:remove_status_effects") + " " + String.join("、", names);
    }

    private String formatPotionEffect(PotionEffect effect, float probability) {
        return formatPotionEffect(effect.getType().getKey().toString(), effect.getAmplifier(), effect.getDuration(), probability);
    }

    private String formatPotionEffect(String type, int amplifier, int durationTicks, float probability) {
        String name = effectName(normalizeEffectKey(type));
        String level = amplifier >= 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1);
        String duration = durationTicks >= 0 ? " " + formatDuration(durationTicks) : "";
        String chance = probability < 0.999f ? " " + formatNumber(probability * 100.0f) + "%" : "";
        return name + " " + level + duration + chance;
    }

    private String normalizeEffectKey(String type) {
        if (type == null || type.isBlank()) {
            return "unknown";
        }
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : "minecraft:" + lower;
    }

    private String effectName(String key) {
        String normalized = normalizeEffectKey(key);
        return effectNames.getOrDefault(normalized, normalized.substring(normalized.indexOf(':') + 1).replace('_', ' '));
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) {
            return unknownText;
        }
        double seconds = ticks / 20.0;
        return formatNumber(seconds) + "秒";
    }

    private String formatNumber(double value) {
        return NUMBER_FORMAT.format(value);
    }

    private List<Component> buildFoodLore(FoodInfo info) {
        List<Component> lines = new ArrayList<>();
        addFormattedLine(lines, headerFormat, info, null);
        addFormattedLine(lines, titleFormat, info, null);
        addFormattedLine(lines, nutritionFormat, info, null);
        addFormattedLine(lines, saturationFormat, info, null);

        List<String> effects = info.effects().isEmpty() ? List.of(noEffectText) : info.effects();
        for (int index = 0; index < effects.size(); index++) {
            addFormattedLine(lines, index == 0 ? effectFormat : extraEffectFormat, info, effects.get(index));
        }
        return lines;
    }

    private void addFormattedLine(List<Component> lines, String format, FoodInfo info, String effect) {
        if (format == null || format.isBlank()) {
            return;
        }
        String line = format
                .replace("%id%", info.id())
                .replace("%source%", info.source())
                .replace("%nutrition%", info.nutrition() == null ? unknownText : formatNumber(info.nutrition()))
                .replace("%saturation%", info.saturation() == null ? unknownText : formatNumber(info.saturation()))
                .replace("%effect%", effect == null ? "" : effect);
        lines.add(parseFormattedLine(line));
    }

    private Component parseFormattedLine(String line) {
        if (line.contains("<") && line.contains(">")) {
            try {
                return withoutItalic(miniMessage.deserialize(line));
            } catch (RuntimeException ignored) {
                return withoutItalic(legacy.deserialize(line));
            }
        }
        return withoutItalic(legacy.deserialize(line));
    }

    private Component withoutItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> removeManagedLore(List<Component> lore) {
        List<Component> cleaned = new ArrayList<>();
        for (Component component : lore) {
            String text = plain.serialize(component);
            if (isManagedLoreLine(text)) {
                continue;
            }
            cleaned.add(component);
        }
        while (!cleaned.isEmpty() && plain.serialize(cleaned.get(cleaned.size() - 1)).isBlank()) {
            cleaned.remove(cleaned.size() - 1);
        }
        return cleaned;
    }

    private boolean isManagedLoreLine(String text) {
        String compact = compactLoreText(text);
        return compact.contains("食物資訊")
                || compact.contains("食物屬性")
                || compact.contains("食用屬性")
                || compact.contains("飽食度")
                || compact.contains("飽和度")
                || compact.startsWith("飽食")
                || compact.startsWith("飽和")
                || compact.contains("特殊效果")
                || compact.startsWith("效果")
                || isGeneratedEffectLine(compact)
                || compact.contains("────────");
    }

    private boolean isGeneratedEffectLine(String compact) {
        if (compact == null || compact.isBlank()) {
            return false;
        }

        if (compact.equals(compactLoreText(hiddenEffectText))) {
            return true;
        }

        if (looksLikeGeneratedPotionEffect(compact)) {
            return true;
        }

        for (String effectPrefix : generatedEffectPrefixes) {
            if (compact.startsWith(effectPrefix) && looksLikeGeneratedPotionEffect(compact.substring(effectPrefix.length()))) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeGeneratedPotionEffect(String compact) {
        if (compact == null || compact.isBlank()) {
            return false;
        }
        return GENERATED_POTION_EFFECT_WITH_DURATION.matcher(compact).matches()
                || GENERATED_POTION_EFFECT_WITH_CHANCE.matcher(compact).matches()
                || GENERATED_POTION_EFFECT_LEVEL_ONLY.matcher(compact).matches();
    }

    private String compactLoreText(String text) {
        return text == null ? "" : text.replace(" ", "").replace("　", "");
    }

    private boolean sameLore(List<Component> first, List<Component> second) {
        return Objects.equals(first, second);
    }

    private void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
    }

    private record FoodInfo(String id, Double nutrition, Double saturation, List<String> effects, String source) {
    }

    private static final class ItemsAdderBridge {
        private final Method byItemStack;
        private final Method getNamespacedId;

        private ItemsAdderBridge(Method byItemStack, Method getNamespacedId) {
            this.byItemStack = byItemStack;
            this.getNamespacedId = getNamespacedId;
        }

        static ItemsAdderBridge detect() {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                return new ItemsAdderBridge(
                        customStack.getMethod("byItemStack", ItemStack.class),
                        customStack.getMethod("getNamespacedID")
                );
            } catch (ReflectiveOperationException exception) {
                return unavailable();
            }
        }

        static ItemsAdderBridge unavailable() {
            return new ItemsAdderBridge(null, null);
        }

        boolean available() {
            return byItemStack != null && getNamespacedId != null;
        }

        String namespacedId(ItemStack item) {
            if (!available()) {
                return null;
            }
            try {
                Object customStack = byItemStack.invoke(null, item);
                if (customStack == null) {
                    return null;
                }
                Object id = getNamespacedId.invoke(customStack);
                return id == null ? null : String.valueOf(id);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
    }
}
