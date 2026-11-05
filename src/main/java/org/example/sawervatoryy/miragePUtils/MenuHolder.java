package org.example.sawervatoryy.miragePUtils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Держатель инвентаря для меню /punish и /history. */
public final class MenuHolder implements InventoryHolder {

    public enum Kind { PUNISH, HISTORY }

    public final Kind kind;
    public final Target target;
    public int page;
    public List<Punishment> entries = List.of();
    public final Map<Integer, String> reasonSlots = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Kind kind, Target target) {
        this.kind = kind;
        this.target = target;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
