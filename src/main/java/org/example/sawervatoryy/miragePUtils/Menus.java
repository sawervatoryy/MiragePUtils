package org.example.sawervatoryy.miragePUtils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Построение GUI: меню наказаний (/punish) и история (/history). В палитре &f + &c. */
public final class Menus {

    public static final int HISTORY_PAGE_SIZE = 45;

    private final PunishManager manager;
    private final Messages msg;

    public Menus(PunishManager manager, Messages msg) {
        this.manager = manager;
        this.msg = msg;
    }

    // ------------------------------------------------------------------ /punish

    public void openPunish(Player staff, Target t, PunishManager.Summary sum) {
        List<ReasonDef> list = new ArrayList<>(manager.reasons());
        int n = Math.min(list.size(), 28);
        int contentRows = Math.max(1, (n + 6) / 7);
        int rows = contentRows + 2;
        MenuHolder h = new MenuHolder(MenuHolder.Kind.PUNISH, t);
        Inventory inv = Bukkit.createInventory(h, rows * 9, Messages.color(msg.format("menu.punish.title", "{player}", t.name())));
        h.setInventory(inv);
        border(inv, rows);
        inv.setItem(4, head(t, sum));
        for (int i = 0; i < n; i++) {
            int slot = 9 * (1 + i / 7) + 1 + (i % 7);
            ReasonDef r = list.get(i);
            inv.setItem(slot, reasonItem(r, sum));
            h.reasonSlots.put(slot, r.id);
        }
        staff.openInventory(inv);
    }

    private ItemStack head(Target t, PunishManager.Summary sum) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = it.getItemMeta();
        if (raw instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(t.uuid()));
            sm.displayName(Messages.item(msg.format("menu.punish.head-name", "{player}", t.name())));
            sm.lore(lore(msg.list("menu.punish.head-lore"), "{total}", sum.total()));
            it.setItemMeta(sm);
        }
        return it;
    }

    private ItemStack reasonItem(ReasonDef r, PunishManager.Summary sum) {
        int count = sum.counts().getOrDefault(r.id, 0);
        ReasonDef.Step step = r.stepFor(count);
        String next = step.type().getDisplayName();
        if (step.type().isTimed() || step.type() == PunishType.WARN) {
            next += " (" + (step.duration() < 0 ? TimeUtil.permanentText() : TimeUtil.format(step.duration())) + ")";
        }
        AnimationType anim = manager.animationFor(step.type(), r.animation);
        List<String> raw = new ArrayList<>(r.description);
        if (!raw.isEmpty()) raw.add(" ");
        raw.addAll(msg.list("menu.punish.lore"));
        ItemStack it = new ItemStack(r.icon);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Messages.item("&c&l" + r.name));
        meta.lore(lore(raw, "{count}", count, "{next}", next, "{anim}", anim.getDisplayName()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ /history

    public void openHistory(Player staff, Target t, List<Punishment> all, int page) {
        int pages = Math.max(1, (all.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        MenuHolder h = new MenuHolder(MenuHolder.Kind.HISTORY, t);
        h.page = pg;
        h.entries = all;
        Inventory inv = Bukkit.createInventory(h, 54, Messages.color(msg.format("menu.history.title", "{player}", t.name())));
        h.setInventory(inv);
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, pane(slot));
        int start = pg * HISTORY_PAGE_SIZE;
        for (int i = 0; i < HISTORY_PAGE_SIZE && start + i < all.size(); i++) {
            inv.setItem(i, historyItem(all.get(start + i)));
        }
        if (pg > 0) inv.setItem(45, nav(Material.ARROW, msg.raw("menu.history.prev")));
        if (pg < pages - 1) inv.setItem(53, nav(Material.ARROW, msg.raw("menu.history.next")));
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.displayName(Messages.item(msg.format("menu.history.info", "{page}", pg + 1, "{pages}", pages, "{total}", all.size())));
        info.setItemMeta(im);
        inv.setItem(49, info);
        staff.openInventory(inv);
    }

    private ItemStack historyItem(Punishment p) {
        Material mat = switch (p.type) {
            case BAN -> Material.BARRIER;
            case IPBAN -> Material.IRON_BARS;
            case MUTE -> Material.NOTE_BLOCK;
            case WARN -> Material.PAPER;
            case KICK -> Material.LEATHER_BOOTS;
        };
        Object[] kv = manager.kv(p);
        List<String> raw = new ArrayList<>(msg.list("menu.history.item-lore"));
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Messages.item(Messages.apply(msg.raw("menu.history.item-name"), kv)));
        meta.lore(lore(raw, kv));
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ вспомогательное

    private List<Component> lore(List<String> lines, Object... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) out.add(Messages.item(Messages.apply(line, kv)));
        return out;
    }

    private void border(Inventory inv, int rows) {
        for (int slot = 0; slot < rows * 9; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) inv.setItem(slot, pane(slot));
        }
    }

    private ItemStack pane(int slot) {
        ItemStack it = new ItemStack(slot % 2 == 0 ? Material.WHITE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack nav(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Messages.item(name));
        it.setItemMeta(m);
        return it;
    }
}
