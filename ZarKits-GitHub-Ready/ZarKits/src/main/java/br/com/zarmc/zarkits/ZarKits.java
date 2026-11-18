package br.com.zarmc.zarkits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class ZarKits extends JavaPlugin implements Listener {
    private final Map<String, KitData> kits = new TreeMap<>();
    private File kitsFolder;
    private File playerDataFile;
    private YamlConfiguration playerData;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) kitsFolder.mkdirs();
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        loadKits();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("zarkit")).setExecutor(this);
        Objects.requireNonNull(getCommand("kits")).setExecutor(this);
        getLogger().info("ZarKits ativado. Kits carregados: " + kits.size());
    }

    @Override
    public void onDisable() { savePlayerData(); }

    static String color(String input) {
        if (input == null) return "";
        String hex = input.replaceAll("(?i)&#([A-F0-9]{6})", "§x§$1");
        // Converte &#RRGGBB para a sequência hexadecimal aceita pelo Minecraft.
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&#([A-F0-9]{6})").matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString().replaceAll("(?i)<#([A-F0-9]{6})>", "&#$1"));
    }

    private void loadKits() {
        kits.clear();
        File[] files = kitsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase();
            kits.put(name, new KitData(this, name, file));
        }
    }

    private String msg(String key, String... replacements) {
        String value = getConfig().getString("messages." + key, "&c" + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        return color(getConfig().getString("messages.prefix", "") + value);
    }

    private KitData kit(String name) { return kits.get(name.toLowerCase(Locale.ROOT)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kits")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return true; }
            if (!player.hasPermission("zarkits.use") && !player.hasPermission("zarkits.admin")) {
                player.sendMessage(msg("no-permission")); return true;
            }
            openMain(player); return true;
        }
        if (args.length == 0) {
            sender.sendMessage(color("&bZarKits &7- /zarkit create|edit|delete|icon|cooldown|permission|reload"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!admin(sender)) return true;
            reloadConfig(); loadKits(); sender.sendMessage(msg("reloaded")); return true;
        }
        if (sub.equals("create") && args.length >= 2) {
            if (!admin(sender)) return true;
            String name = safeName(args[1]);
            if (name == null) { sender.sendMessage(color("&cNome inválido.")); return true; }
            if (kit(name) != null) { sender.sendMessage(color("&cEsse kit já existe.")); return true; }
            File file = new File(kitsFolder, name + ".yml");
            KitData data = new KitData(this, name, file);
            data.yaml.set("cooldown", 0);
            data.yaml.set("one_time", false);
            data.yaml.set("auto_armor", true);
            data.yaml.set("permission_required", false);
            data.yaml.set("permission", "zarkits.kit." + name);
            data.yaml.set("clear_inventory", false);
            data.yaml.set("save_original_items", true);
            data.yaml.set("display.default.id", "CHEST");
            data.yaml.set("display.default.name", "&b&l" + name.toUpperCase(Locale.ROOT));
            data.yaml.set("display.default.lore", List.of("&7Clique no ícone para ver o kit.", "&7Use o papel para resgatar."));
            data.yaml.set("display.no_permission.id", "BARRIER");
            data.yaml.set("display.no_permission.name", "&c" + name);
            data.yaml.set("display.cooldown.id", "CLOCK");
            data.yaml.set("display.cooldown.name", "&e" + name);
            data.yaml.set("display.one_time.id", "BARRIER");
            data.yaml.set("display.one_time.name", "&c" + name + " &7(já resgatado)");
            data.yaml.set("items", new LinkedHashMap<>());
            data.save(); kits.put(name, data);
            sender.sendMessage(msg("kit-created", "%kit%", name));
            if (sender instanceof Player player) openEdit(player, name);
            return true;
        }
        if (sub.equals("edit") && args.length >= 2) {
            if (!admin(sender)) return true;
            if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return true; }
            if (kit(args[1]) == null) { sender.sendMessage(msg("kit-not-found")); return true; }
            openEdit(player, args[1]); return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            if (!admin(sender)) return true;
            KitData data = kit(args[1]);
            if (data == null) { sender.sendMessage(msg("kit-not-found")); return true; }
            kits.remove(data.name); if (data.file.exists()) data.file.delete();
            sender.sendMessage(msg("kit-deleted", "%kit%", data.name)); return true;
        }
        if (sub.equals("icon") && args.length >= 2) {
            if (!admin(sender)) return true;
            if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return true; }
            KitData data = kit(args[1]);
            if (data == null) { player.sendMessage(msg("kit-not-found")); return true; }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { player.sendMessage(color("&cSegure o item que deseja usar como ícone.")); return true; }
            data.yaml.set("display.default.id", hand.getType().name());
            data.save(); player.sendMessage(msg("kit-saved", "%kit%", data.name)); return true;
        }
        if (sub.equals("cooldown") && args.length >= 3) {
            if (!admin(sender)) return true;
            KitData data = kit(args[1]);
            if (data == null) { sender.sendMessage(msg("kit-not-found")); return true; }
            try { data.yaml.set("cooldown", Math.max(0, Long.parseLong(args[2]))); data.save(); sender.sendMessage(msg("kit-saved", "%kit%", data.name)); }
            catch (NumberFormatException ex) { sender.sendMessage(color("&cInforme o cooldown em segundos.")); }
            return true;
        }
        if (sub.equals("permission") && args.length >= 3) {
            if (!admin(sender)) return true;
            KitData data = kit(args[1]);
            if (data == null) { sender.sendMessage(msg("kit-not-found")); return true; }
            boolean enabled = !args[2].equalsIgnoreCase("none");
            data.yaml.set("permission_required", enabled);
            data.yaml.set("permission", enabled ? args[2] : "zarkits.kit." + data.name);
            data.save(); sender.sendMessage(msg("kit-saved", "%kit%", data.name)); return true;
        }
        sender.sendMessage(color("&cUso: /zarkit create <nome> | edit <nome> | delete <nome> | icon <nome> | cooldown <nome> <segundos> | permission <nome> <permissão|none> | reload"));
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("zarkits.admin")) return true;
        sender.sendMessage(msg("no-permission")); return false;
    }

    private String safeName(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        return name.matches("[a-z0-9_-]{1,32}") ? name : null;
    }

    private Inventory createInventory(MenuHolder.Type type, String kit, int size, String title) {
        MenuHolder holder = new MenuHolder(type, kit);
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.setInventory(inv);
        return inv;
    }

    private void openMain(Player player) {
        int size = getConfig().getInt("menu.size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) size = 54;
        Inventory inv = createInventory(MenuHolder.Type.MAIN, null, size, getConfig().getString("menu.title", "&8Kits"));
        List<Integer> slots = getConfig().getIntegerList("menu.icon-slots");
        if (slots.isEmpty()) slots = List.of(10, 12, 14, 16, 18, 28, 30, 32, 34, 36);
        int index = 0;
        for (KitData data : kits.values()) {
            if (index >= slots.size()) break;
            int iconSlot = slots.get(index++);
            if (iconSlot < 0 || iconSlot >= size || iconSlot + 9 >= size) continue;
            String state = state(player, data);
            String displayState = state.equals("NO_PERMISSION") ? "no_permission" : state.equals("COOLDOWN") ? "cooldown" : state.equals("ONE_TIME") ? "one_time" : "default";
            ItemStack icon = data.displayItem(displayState, state.equals("COOLDOWN") ? formatTime(remaining(player, data)) : "");
            inv.setItem(iconSlot, icon);
            ItemStack paper = makeConfiguredItem(
                    getConfig().getString("menu.claim-material", "PAPER"),
                    getConfig().getString("menu.claim-name", "&aResgatar kit"),
                    getConfig().getStringList("menu.claim-lore"));
            if (state.equals("COOLDOWN")) {
                ItemMeta meta = paper.getItemMeta();
                if (meta != null) {
                    meta.setLore(List.of(color("&cAguarde: " + formatTime(remaining(player, data)))));
                    paper.setItemMeta(meta);
                }
            } else if (state.equals("NO_PERMISSION")) {
                ItemMeta meta = paper.getItemMeta();
                if (meta != null) { meta.setDisplayName(color("&cSem permissão")); paper.setItemMeta(meta); }
            }
            inv.setItem(iconSlot + 9, paper);
        }
        player.openInventory(inv);
    }

    private ItemStack makeConfiguredItem(String materialName, String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) meta.setLore(lore.stream().map(ZarKits::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openPreview(Player player, String kitName) {
        KitData data = kit(kitName);
        if (data == null) return;
        Inventory inv = createInventory(MenuHolder.Type.PREVIEW, data.name, 54,
                getConfig().getString("menu.preview-title", "&8Preview: %kit%").replace("%kit%", data.name));
        for (KitData.PreviewItem item : data.previewItems()) {
            if (item.slot() >= 0 && item.slot() < 45) inv.setItem(item.slot(), item.item());
        }
        inv.setItem(49, makeConfiguredItem(getConfig().getString("menu.back-material", "ARROW"),
                getConfig().getString("menu.back-name", "&cVoltar"), List.of("&7Voltar aos kits")));
        player.openInventory(inv);
    }

    private void openEdit(Player player, String kitName) {
        KitData data = kit(kitName);
        if (data == null) return;
        Inventory inv = createInventory(MenuHolder.Type.EDIT, data.name, 27,
                getConfig().getString("menu.editor-title", "&8Editor: %kit%").replace("%kit%", data.name));
        inv.setItem(10, makeConfiguredItem("CHEST", "&aEditar itens", List.of("&7Clique para editar os itens do kit.")));
        inv.setItem(12, makeConfiguredItem("CLOCK", "&eCooldown", List.of("&7Atual: " + data.cooldown() + " segundos", "&7Use /zarkit cooldown " + data.name + " <segundos>")));
        inv.setItem(14, makeConfiguredItem("ITEM_FRAME", "&bÍcone", List.of("&7Segure um item e use", "&f/zarkit icon " + data.name)));
        inv.setItem(16, makeConfiguredItem("NAME_TAG", "&dPermissão", List.of("&7Atual: " + data.permission(), "&7Use /zarkit permission " + data.name + " <permissão|none>")));
        inv.setItem(22, makeConfiguredItem("LIME_DYE", "&aSalvar", List.of("&7Os itens são salvos ao fechar o editor.")));
        player.openInventory(inv);
    }

    private void openItemsEditor(Player player, String kitName) {
        KitData data = kit(kitName);
        if (data == null) return;
        Inventory inv = createInventory(MenuHolder.Type.ITEMS, data.name, 54,
                getConfig().getString("menu.edit-items-title", "&8Itens: %kit%").replace("%kit%", data.name));
        for (KitData.PreviewItem item : data.previewItems()) {
            if (item.slot() >= 0 && item.slot() < 54) inv.setItem(item.slot(), item.item());
        }
        player.openInventory(inv);
    }

    private String state(Player player, KitData data) {
        if (data.permissionRequired() && !player.hasPermission(data.permission())) return "NO_PERMISSION";
        if (data.oneTime() && playerData.getBoolean("players." + player.getUniqueId() + ".claimed." + data.name, false)) return "ONE_TIME";
        if (remaining(player, data) > 0) return "COOLDOWN";
        return "AVAILABLE";
    }

    private long remaining(Player player, KitData data) {
        long until = playerData.getLong("players." + player.getUniqueId() + ".cooldowns." + data.name, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private String formatTime(long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private void claim(Player player, KitData data) {
        String state = state(player, data);
        if (state.equals("NO_PERMISSION")) { player.sendMessage(msg("no-kit-permission")); return; }
        if (state.equals("ONE_TIME")) { player.sendMessage(msg("already-claimed")); return; }
        if (state.equals("COOLDOWN")) { player.sendMessage(msg("cooldown", "%time%", formatTime(remaining(player, data)))); return; }
        List<ItemStack> items = data.items();
        if (!data.clearInventory()) {
            int empty = 0;
            for (ItemStack item : items) if (item != null && item.getType() != Material.AIR) empty++;
            if (player.getInventory().firstEmpty() == -1 && empty > 0) { player.sendMessage(msg("inventory-full")); return; }
        } else player.getInventory().clear();

        for (ItemStack item : items) {
            ItemStack clone = item.clone();
            if (data.autoArmor() && isArmor(clone.getType())) {
                var inv = player.getInventory();
                if (clone.getType().name().endsWith("_HELMET") && inv.getHelmet() == null) { inv.setHelmet(clone); continue; }
                if (clone.getType().name().endsWith("_CHESTPLATE") && inv.getChestplate() == null) { inv.setChestplate(clone); continue; }
                if (clone.getType().name().endsWith("_LEGGINGS") && inv.getLeggings() == null) { inv.setLeggings(clone); continue; }
                if (clone.getType().name().endsWith("_BOOTS") && inv.getBoots() == null) { inv.setBoots(clone); continue; }
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(clone);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (data.cooldown() > 0) playerData.set("players." + player.getUniqueId() + ".cooldowns." + data.name,
                System.currentTimeMillis() + data.cooldown() * 1000L);
        if (data.oneTime()) playerData.set("players." + player.getUniqueId() + ".claimed." + data.name, true);
        savePlayerData();
        player.sendMessage(msg("kit-claimed", "%kit%", data.name));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        openMain(player);
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private void savePlayerData() {
        try { playerData.save(playerDataFile); }
        catch (IOException ex) { getLogger().severe("Falha ao salvar playerdata.yml: " + ex.getMessage()); }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) return;
        if (holder.type != MenuHolder.Type.ITEMS) event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;

        switch (holder.type) {
            case MAIN -> {
                KitData data = kitAtSlot(raw);
                if (data == null) return;
                int iconSlot = raw;
                if (isClaimSlot(raw)) {
                    int corresponding = raw - 9;
                    data = kitAtSlot(corresponding);
                    if (data != null) claim(player, data);
                } else openPreview(player, data.name);
            }
            case PREVIEW -> {
                if (raw == 49) openMain(player);
            }
            case EDIT -> {
                if (raw == 10) openItemsEditor(player, holder.kit);
                else if (raw == 12) player.closeInventory();
            }
            case ITEMS -> { /* inventário editável; salvo ao fechar */ }
        }
    }

    private KitData kitAtSlot(int slot) {
        List<Integer> slots = getConfig().getIntegerList("menu.icon-slots");
        if (slots.isEmpty()) slots = List.of(10, 12, 14, 16, 18, 28, 30, 32, 34, 36);
        int index = slots.indexOf(slot);
        if (index < 0) index = slots.indexOf(slot - 9);
        if (index < 0 || index >= kits.size()) return null;
        return new ArrayList<>(kits.values()).get(index);
    }

    private boolean isClaimSlot(int slot) {
        List<Integer> slots = getConfig().getIntegerList("menu.icon-slots");
        if (slots.isEmpty()) slots = List.of(10, 12, 14, 16, 18, 28, 30, 32, 34, 36);
        return slots.contains(slot - 9);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder
                && holder.type != MenuHolder.Type.ITEMS) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder) || holder.type != MenuHolder.Type.ITEMS) return;
        KitData data = kit(holder.kit);
        if (data == null) return;
        Inventory inv = event.getInventory();
        data.yaml.set("items", null);
        int number = 1;
        for (int slot = 0; slot < 54; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            String path = "items." + number;
            data.yaml.set(path + ".original", item.clone());
            data.yaml.set(path + ".preview_slot", slot);
            number++;
        }
        data.save();
        event.getPlayer().sendMessage(msg("kit-saved", "%kit%", data.name));
    }
}
