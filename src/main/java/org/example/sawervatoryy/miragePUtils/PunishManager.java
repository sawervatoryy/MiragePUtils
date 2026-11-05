package org.example.sawervatoryy.miragePUtils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Ядро плагина: лесенка наказаний, анимации и выдача наказаний через команды LiteBans.
 * Сам плагин баны/муты не хранит — он запускает команды LiteBans (от имени модератора).
 */
public final class PunishManager {

    @FunctionalInterface
    public interface DbTask {
        void run() throws Exception;
    }

    public record Summary(int total, Map<String, Integer> counts) {
    }

    private static final Map<String, String> DEFAULT_TEMPLATES = Map.of(
            "ban", "ban {silent}{player} {reason}",
            "tempban", "tempban {silent}{player} {duration} {reason}",
            "ipban", "ipban {silent}{player} {reason}",
            "tempipban", "tempipban {silent}{player} {duration} {reason}",
            "mute", "mute {silent}{player} {reason}",
            "tempmute", "tempmute {silent}{player} {duration} {reason}",
            "warn", "warn {silent}{player} {reason}",
            "tempwarn", "tempwarn {silent}{player} {duration} {reason}",
            "kick", "kick {silent}{player} {reason}");

    private final MiragePUtils plugin;
    private final Database db;
    private final Messages msg;
    private final DiscordWebhook webhook;
    private final Animations animations;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MiragePUtils-DB");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ReasonDef> reasons = new LinkedHashMap<>();

    public PunishManager(MiragePUtils plugin, Database db, Messages msg, DiscordWebhook webhook, Animations animations) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.webhook = webhook;
        this.animations = animations;
        reload();
    }

    // ------------------------------------------------------------------ конфигурация

    public void reload() {
        Map<String, ReasonDef> fresh = new LinkedHashMap<>();
        long warnDefault = defaultWarnDuration();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("reasons");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r == null) continue;
                List<ReasonDef.Step> steps = new ArrayList<>();
                for (String line : r.getStringList("ladder")) {
                    ReasonDef.Step st = parseStep(line, warnDefault);
                    if (st == null) {
                        plugin.getLogger().warning("Причина '" + id + "': неверный шаг лесенки '" + line + "'");
                    } else {
                        steps.add(st);
                    }
                }
                if (steps.isEmpty()) {
                    plugin.getLogger().warning("Причина '" + id + "' пропущена: нет корректных шагов ladder.");
                    continue;
                }
                Material icon = Material.matchMaterial(r.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;
                String perm = r.getString("permission", "");
                String anim = r.getString("animation", "");
                fresh.put(id.toLowerCase(Locale.ROOT), new ReasonDef(id.toLowerCase(Locale.ROOT),
                        r.getString("name", id), icon, r.getStringList("description"),
                        perm == null ? "" : perm, anim == null ? "" : anim, steps));
            }
        }
        synchronized (reasons) {
            reasons.clear();
            reasons.putAll(fresh);
        }
    }

    public long defaultWarnDuration() {
        long d = TimeUtil.parse(plugin.getConfig().getString("warns.default-duration", "30d"));
        return d == TimeUtil.INVALID ? TimeUtil.PERMANENT : d;
    }

    private ReasonDef.Step parseStep(String line, long warnDefault) {
        if (line == null || line.isBlank()) return null;
        String[] t = line.trim().split("\\s+");
        PunishType type = PunishType.parse(t[0]);
        if (type == null) return null;
        long duration;
        if (type == PunishType.KICK) {
            duration = TimeUtil.PERMANENT;
        } else if (type == PunishType.WARN) {
            duration = t.length > 1 ? TimeUtil.parse(t[1]) : warnDefault;
        } else {
            duration = t.length > 1 ? TimeUtil.parse(t[1]) : TimeUtil.PERMANENT;
        }
        if (duration == TimeUtil.INVALID) return null;
        return new ReasonDef.Step(type, duration);
    }

    public Collection<ReasonDef> reasons() {
        synchronized (reasons) {
            return new ArrayList<>(reasons.values());
        }
    }

    public List<String> reasonIds() {
        synchronized (reasons) {
            return new ArrayList<>(reasons.keySet());
        }
    }

    /** Какая анимация будет проиграна для наказания (с учётом настроек и шаблона причины). */
    public AnimationType animationFor(PunishType type, String override) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("animations.enabled", true)) return AnimationType.NONE;
        AnimationType t = AnimationType.parse(override);
        if (t == null) t = AnimationType.parse(cfg.getString("animations.by-type." + type.name()));
        if (t == null) t = AnimationType.defaultFor(type);
        return t;
    }

    // ------------------------------------------------------------------ потоки

    public void async(DbTask task, CommandSender notify) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка базы данных", ex);
                    if (notify != null) sync(() -> msg.send(notify, "db-error"));
                }
            });
        } catch (RuntimeException ignored) {
            // executor остановлен при выключении плагина
        }
    }

    public void sync(Runnable r) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ поиск цели

    /** Находит игрока (онлайн, из кэша сервера или среди заходивших). Callback в основном потоке; null = не найден. */
    public void resolve(String input, Consumer<Target> callback) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            callback.accept(new Target(online.getUniqueId(), online.getName()));
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null) {
            callback.accept(new Target(cached.getUniqueId(), cached.getName() != null ? cached.getName() : input));
            return;
        }
        async(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(input);
            Target t = null;
            if (op.hasPlayedBefore() || op.isOnline()) {
                t = new Target(op.getUniqueId(), op.getName() != null ? op.getName() : input);
            }
            final Target result = t;
            sync(() -> callback.accept(result));
        }, Bukkit.getConsoleSender());
    }

    // ------------------------------------------------------------------ выдача

    /** Выдача по шаблону причины с учётом числа прошлых нарушений («лесенка»). */
    public void applyReason(CommandSender staff, Target target, String reasonId, boolean silent) {
        ReasonDef r;
        synchronized (reasons) {
            r = reasons.get(reasonId.toLowerCase(Locale.ROOT));
        }
        if (r == null) {
            msg.send(staff, "unknown-reason", "{reason}", reasonId);
            return;
        }
        if (!r.permission.isEmpty() && !staff.hasPermission(r.permission)) {
            msg.send(staff, "no-permission");
            return;
        }
        final ReasonDef reason = r;
        async(() -> {
            int count = db.categoryCounts(target.uuid()).getOrDefault(reason.id, 0);
            sync(() -> {
                ReasonDef.Step step = reason.stepFor(count);
                issue(staff, target, step.type(), step.duration(), reason.name, reason.id, silent, reason.animation);
            });
        }, staff);
    }

    /** Проверки, анимация и затем команда LiteBans. */
    public void issue(CommandSender staff, Target target, PunishType type, long duration, String reason,
                      String category, boolean silent, String animOverride) {
        if (staff instanceof Player sp && sp.getUniqueId().equals(target.uuid())) {
            msg.send(staff, "cannot-self");
            return;
        }
        if (animations.isAnimating(target.uuid())) {
            msg.send(staff, "already-animating", "{player}", target.name());
            return;
        }
        String key = commandKey(type, duration);
        FileConfiguration cfg = plugin.getConfig();
        if (staff instanceof Player && cfg.getBoolean("litebans.check-permissions", true)
                && !staff.hasPermission("litebans." + key)) {
            msg.send(staff, "no-litebans-permission", "{perm}", "litebans." + key);
            return;
        }
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null && staff instanceof Player && cfg.getBoolean("litebans.check-exempt", true)
                && online.hasPermission("litebans.exempt." + exemptKey(type))) {
            msg.send(staff, "target-exempt", "{player}", target.name());
            return;
        }
        final String command = buildCommand(key, target.name(), duration, reason, silent);
        final AnimationType anim = animationFor(type, animOverride);
        Runnable finish = () -> complete(staff, target, type, duration, reason, category, silent, anim, command);
        if (online != null && anim != AnimationType.NONE
                && animations.start(online, anim, type.getDisplayName(), Messages.strip(reason), finish)) {
            msg.send(staff, "animation-started", "{anim}", anim.getDisplayName(), "{player}", target.name());
        } else {
            finish.run();
        }
    }

    private void complete(CommandSender staff, Target target, PunishType type, long duration, String reason,
                          String category, boolean silent, AnimationType anim, String command) {
        CommandSender runner = staff;
        boolean asConsole = !plugin.getConfig().getString("litebans.execute-as", "staff").equalsIgnoreCase("staff");
        if (asConsole || (staff instanceof Player && !((Player) staff).isOnline())) {
            runner = Bukkit.getConsoleSender();
        }
        Bukkit.dispatchCommand(runner, command);

        Punishment p = new Punishment();
        p.type = type;
        p.targetUuid = target.uuid();
        p.targetName = target.name();
        p.staffUuid = staff instanceof Player ? ((Player) staff).getUniqueId() : null;
        p.staffName = staff instanceof Player ? staff.getName() : msg.raw("console-name");
        p.reason = reason;
        p.category = category == null ? "manual" : category;
        p.created = System.currentTimeMillis();
        p.duration = duration < 0 ? -1 : duration;
        p.silent = silent;
        p.animation = anim.name();
        async(() -> {
            p.id = db.insert(p);
        }, null);

        webhook.send("Наказание — " + type.getDisplayName(),
                "**Игрок:** " + target.name() + "\n**Модератор:** " + p.staffName
                        + "\n**Причина:** " + Messages.strip(reason)
                        + (type == PunishType.KICK ? ""
                        : "\n**Срок:** " + (p.isPermanent() ? TimeUtil.permanentText() : TimeUtil.format(p.duration)))
                        + "\n**Анимация:** " + anim.getDisplayName(),
                DiscordWebhook.COLOR_RED);
    }

    private static String commandKey(PunishType type, long duration) {
        boolean temp = duration >= 0;
        return switch (type) {
            case BAN -> temp ? "tempban" : "ban";
            case IPBAN -> temp ? "tempipban" : "ipban";
            case MUTE -> temp ? "tempmute" : "mute";
            case WARN -> temp ? "tempwarn" : "warn";
            case KICK -> "kick";
        };
    }

    private static String exemptKey(PunishType type) {
        return switch (type) {
            case BAN, IPBAN -> "ban";
            case MUTE -> "mute";
            case WARN -> "warn";
            case KICK -> "kick";
        };
    }

    private String buildCommand(String key, String player, long duration, String reason, boolean silent) {
        FileConfiguration cfg = plugin.getConfig();
        String template = cfg.getString("litebans.commands." + key, DEFAULT_TEMPLATES.get(key));
        String flag = silent ? cfg.getString("litebans.silent-flag", "-s") + " " : "";
        String cmd = template
                .replace("{silent}", flag)
                .replace("{player}", player)
                .replace("{duration}", duration < 0 ? "" : TimeUtil.litebans(duration))
                .replace("{reason}", reason);
        return cmd.trim().replaceAll("\\s{2,}", " ");
    }

    // ------------------------------------------------------------------ запросы для команд и меню

    public void loadSummary(CommandSender notify, Target t, Consumer<Summary> cb) {
        async(() -> {
            Summary s = new Summary(db.countTotal(t.uuid()), db.categoryCounts(t.uuid()));
            sync(() -> cb.accept(s));
        }, notify);
    }

    public void loadHistory(CommandSender notify, Target t, Consumer<List<Punishment>> cb) {
        async(() -> {
            List<Punishment> list = db.history(t.uuid(), 500);
            sync(() -> cb.accept(list));
        }, notify);
    }

    public void resetCounts(CommandSender staff, Target t, Consumer<Integer> cb) {
        final String by = staff instanceof Player ? staff.getName() : msg.raw("console-name");
        async(() -> {
            int n = db.resetCounts(t.uuid(), by);
            sync(() -> cb.accept(n));
        }, staff);
    }

    // ------------------------------------------------------------------ текст

    /** Подстановки для сообщений о наказании. */
    public Object[] kv(Punishment p) {
        AnimationType anim = AnimationType.parse(p.animation);
        List<Object> l = new ArrayList<>();
        l.add("{id}");
        l.add(p.id);
        l.add("{type}");
        l.add(p.type.getDisplayName());
        l.add("{player}");
        l.add(p.targetName);
        l.add("{staff}");
        l.add(p.staffName);
        l.add("{reason}");
        l.add(p.reason);
        l.add("{category}");
        l.add(p.category);
        l.add("{date}");
        l.add(TimeUtil.date(p.created));
        l.add("{duration}");
        l.add(p.type == PunishType.KICK ? "-" : (p.isPermanent() ? TimeUtil.permanentText() : TimeUtil.format(p.duration)));
        l.add("{state}");
        l.add(p.resetBy != null ? msg.raw("state.reset") : msg.raw("state.issued"));
        l.add("{animation}");
        l.add(anim == null ? "-" : anim.getDisplayName());
        return l.toArray();
    }
}
