package br.com.zarmc.zarkits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class KitData {
    final ZarKits plugin;
    final String name;
    final File file;
    YamlConfiguration yaml;

    KitData(ZarKits plugin, String name, File file) {
        this.plugin = plugin;
        this.name = name.toLowerCase();
        this.file = file;
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    void save() {
        try { yaml.save(file); }
        catch (IOException ex) {
            plugin.getLogger().severe("Não foi possível salvar o kit " + name + ": " + ex.getMessage());
        }
    }

    long cooldown() { return Math.max(0L, yaml.getLong("cooldown", 0L)); }
    boolean oneTime() { return yaml.getBoolean("one_time", false); }
    boolean permissionRequired() { return yaml.getBoolean("permission_required", false); }
    String permission() { return yaml.getString("permission", "zarkits.kit." + name); }
    boolean clearInventory() { return yaml.getBoolean("clear_inventory", false); }
    boolean autoArmor() { return yaml.getBoolean("auto_armor", true); }

    List<ItemStack> items() {
        List<ItemStack> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) continue;
            ItemStack stack = itemSection.getItemStack("original");
            if (stack == null) stack = itemSection.getItemStack("item");
            if (stack != null && stack.getType() != Material.AIR) result.add(stack.clone());
        }
        return result;
    }

    List<PreviewItem> previewItems() {
        List<PreviewItem> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) continue;
            ItemStack stack = itemSection.getItemStack("original");
            if (stack == null) stack = itemSection.getItemStack("item");
            if (stack == null || stack.getType() == Material.AIR) continue;
            result.add(new PreviewItem(itemSection.getInt("preview_slot", result.size()), stack.clone()));
        }
        result.sort(Comparator.comparingInt(PreviewItem::slot));
        return result;
    }

    ItemStack displayItem(String state, String time) {
        String path = "display." + state;
        String materialName = yaml.getString(path + ".id", state.equals("no_permission") ? "BARRIER" : "CHEST");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.CHEST;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, yaml.getInt(path + ".amount", 1))));
        var meta = item.getItemMeta();
        if (meta != null) {
            String displayName = yaml.getString(path + ".name", "&b" + name);
            meta.setDisplayName(ZarKits.color(displayName.replace("%kit_name%", name)));
            List<String> lore = yaml.getStringList(path + ".lore");
            if (!lore.isEmpty()) meta.setLore(lore.stream()
                    .map(line -> ZarKits.color(line.replace("%kit_name%", name).replace("%time%", time))
                    ).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    record PreviewItem(int slot, ItemStack item) {}
}
