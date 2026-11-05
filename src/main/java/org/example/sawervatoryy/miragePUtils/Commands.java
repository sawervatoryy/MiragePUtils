package org.example.sawervatoryy.miragePUtils;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Команды плагина: /punish, /phistory, /mirageputils. Остальное делает LiteBans. */
public final class Commands implements CommandExecutor, TabCompleter {

    private final MiragePUtils plugin;
    private final PunishManager manager;
    private final Messages msg;
    private final Menus menus;
    private final Animations animations;

    public Commands(MiragePUtils plugin, PunishManager manager, Messages msg, Menus menus, Animations animations) {
        this.plugin = plugin;
        this.manager = manager;
        this.msg = msg;
        this.menus = menus;
        this.animations = animations;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "punish" -> punish(s, a);
            case "phistory" -> history(s, a);
            case "mirageputils" -> main(s, a);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ /punish

    private void punish(CommandSender s, String[] a) {
        if (deny(s, "mirageputils.punish")) return;
        if (a.length < 1) {
            msg.send(s, "usage", "{usage}", "/punish <игрок> [причина] [-s]");
            return;
        }
        if (a.length == 1) {
            if (!(s instanceof Player)) {
                msg.send(s, "player-only");
                return;
            }
            Player pl = (Player) s;
            manager.resolve(a[0], t -> {
                if (t == null) {
                    msg.send(s, "player-not-found", "{player}", a[0]);
                    return;
                }
                manager.loadSummary(s, t, sum -> menus.openPunish(pl, t, sum));
            });
            return;
        }
        boolean silent = a.length > 2 && a[2].equalsIgnoreCase("-s");
        if (silent && !s.hasPermission("mirageputils.silent")) {
            msg.send(s, "silent-denied");
            return;
        }
        manager.resolve(a[0], t -> {
            if (t == null) {
                msg.send(s, "player-not-found", "{player}", a[0]);
                return;
            }
            manager.applyReason(s, t, a[1], silent);
        });
    }

    // ------------------------------------------------------------------ /phistory

    private void history(CommandSender s, String[] a) {
        if (deny(s, "mirageputils.history")) return;
        if (a.length < 1) {
            msg.send(s, "usage", "{usage}", "/phistory <игрок> [страница]");
            return;
        }
        int page = 1;
        if (a.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(a[1]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        final int pg = page;
        manager.resolve(a[0], t -> {
            if (t == null) {
                msg.send(s, "player-not-found", "{player}", a[0]);
                return;
            }
            manager.loadHistory(s, t, list -> {
                if (s instanceof Player) {
                    menus.openHistory((Player) s, t, list, pg - 1);
                    return;
                }
                msg.send(s, "history-header", "{player}", t.name(), "{total}", list.size());
                for (int i = 0; i < Math.min(15, list.size()); i++) {
                    msg.send(s, "history-line", manager.kv(list.get(i)));
                }
            });
        });
    }

    // ------------------------------------------------------------------ /mirageputils

    private void main(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "about";
        switch (sub) {
            case "reload" -> {
                if (deny(s, "mirageputils.admin")) return;
                plugin.reloadAll();
                msg.send(s, "reloaded");
            }
            case "preview" -> {
                if (deny(s, "mirageputils.admin")) return;
                if (!(s instanceof Player)) {
                    msg.send(s, "player-only");
                    return;
                }
                AnimationType type = a.length > 1 ? AnimationType.parse(a[1]) : null;
                if (type == null || type == AnimationType.NONE) {
                    msg.send(s, "usage", "{usage}", "/mirageputils preview <" + animationNames() + ">");
                    return;
                }
                Player p = (Player) s;
                if (!animations.start(p, type, msg.raw("preview-type"), msg.raw("preview-reason"), () -> { })) {
                    msg.send(s, "already-animating", "{player}", p.getName());
                }
            }
            case "reset" -> {
                if (deny(s, "mirageputils.admin")) return;
                if (a.length < 2) {
                    msg.send(s, "usage", "{usage}", "/mirageputils reset <игрок>");
                    return;
                }
                manager.resolve(a[1], t -> {
                    if (t == null) {
                        msg.send(s, "player-not-found", "{player}", a[1]);
                        return;
                    }
                    manager.resetCounts(s, t, n -> msg.send(s, "reset-done", "{player}", t.name(), "{count}", n));
                });
            }
            case "help" -> msg.sendList(s, "help");
            default -> msg.sendList(s, "about");
        }
    }

    // ------------------------------------------------------------------ утилиты

    private boolean deny(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return false;
        msg.send(s, "no-permission");
        return true;
    }

    private String animationNames() {
        List<String> names = new ArrayList<>();
        for (AnimationType t : AnimationType.values()) {
            if (t != AnimationType.NONE) names.add(t.name().toLowerCase(Locale.ROOT));
        }
        return String.join("|", names);
    }

    // ------------------------------------------------------------------ автодополнение

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("mirageputils")) {
            if (a.length == 1) return filter(List.of("reload", "about", "help", "preview", "reset"), a[0]);
            if (a.length == 2 && a[0].equalsIgnoreCase("preview")) {
                List<String> names = new ArrayList<>();
                for (AnimationType t : AnimationType.values()) {
                    if (t != AnimationType.NONE) names.add(t.name().toLowerCase(Locale.ROOT));
                }
                return filter(names, a[1]);
            }
            if (a.length == 2 && a[0].equalsIgnoreCase("reset")) return filter(onlineNames(), a[1]);
            return List.of();
        }
        if (a.length == 1) return filter(onlineNames(), a[0]);
        if (a.length == 2 && name.equals("punish")) return filter(manager.reasonIds(), a[1]);
        if (a.length == 3 && name.equals("punish")) return filter(List.of("-s"), a[2]);
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
