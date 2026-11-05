package org.example.sawervatoryy.miragePUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Обработка кликов в меню MiragePUtils (все предметы заблокированы от перемещения). */
public final class MenuListener implements Listener {

    private final MiragePUtils plugin;
    private final PunishManager manager;
    private final Menus menus;

    public MenuListener(MiragePUtils plugin, PunishManager manager, Menus menus) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int slot = e.getRawSlot();

        if (holder.kind == MenuHolder.Kind.PUNISH) {
            String reasonId = holder.reasonSlots.get(slot);
            if (reasonId == null) return;
            boolean silent = e.isShiftClick() && player.hasPermission("mirageputils.silent");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                manager.applyReason(player, holder.target, reasonId, silent);
            });
            return;
        }

        int pages = Math.max(1, (holder.entries.size() + Menus.HISTORY_PAGE_SIZE - 1) / Menus.HISTORY_PAGE_SIZE);
        if (slot == 45 && holder.page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> menus.openHistory(player, holder.target, holder.entries, holder.page - 1));
        } else if (slot == 53 && holder.page < pages - 1) {
            Bukkit.getScheduler().runTask(plugin, () -> menus.openHistory(player, holder.target, holder.entries, holder.page + 1));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }
}
