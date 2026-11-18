package br.com.zarmc.zarkits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class MenuHolder implements InventoryHolder {
    enum Type { MAIN, PREVIEW, EDIT, ITEMS }
    final Type type;
    final String kit;
    private Inventory inventory;

    MenuHolder(Type type, String kit) {
        this.type = type;
        this.kit = kit;
    }

    void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
