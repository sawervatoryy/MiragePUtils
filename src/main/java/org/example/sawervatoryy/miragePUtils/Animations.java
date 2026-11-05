package org.example.sawervatoryy.miragePUtils;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Анимации наказаний: игрок зависает, взлетает по спирали, затем вспышка, взрыв и молния —
 * и только после этого выполняется наказание. Палитра частиц: белый + красный.
 */
public final class Animations {

    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.3f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 1.3f);

    private final MiragePUtils plugin;
    private final Messages msg;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public Animations(MiragePUtils plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    public boolean isAnimating(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /** @return false, если анимация не запущена (тип NONE или игрок уже в анимации). */
    public boolean start(Player target, AnimationType type, String typeText, String reason, Runnable onFinish) {
        if (type == AnimationType.NONE || sessions.containsKey(target.getUniqueId())) return false;
        Session s = new Session(target, type, onFinish);
        sessions.put(target.getUniqueId(), s);
        s.begin(typeText, reason);
        return true;
    }

    /** Игрок вышел во время анимации — наказание применяется сразу. */
    public void finishNow(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s != null) s.finish(true, true);
    }

    /** Остановка при выключении плагина: состояние игроков восстанавливается, наказания не применяются. */
    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) s.finish(true, false);
        sessions.clear();
    }

    // ------------------------------------------------------------------ частицы

    private static void ring(World w, double cx, double cy, double cz, double r, int points, double phase,
                             Particle.DustOptions opt) {
        for (int i = 0; i < points; i++) {
            double a = phase + i * 2 * Math.PI / points;
            w.spawnParticle(Particle.DUST, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0, 0, 0, 0, opt);
        }
    }

    private static void burst(World w, Location l) {
        for (int i = 0; i < 24; i++) {
            double a = i * 2 * Math.PI / 24;
            w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + 1, l.getZ(), 0,
                    Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5, 1.0);
        }
    }

    // ------------------------------------------------------------------ сессия

    private final class Session {
        final Player target;
        final AnimationType type;
        final Runnable onFinish;
        Location origin;
        double rise;
        int tick;
        BukkitTask task;
        boolean done;
        boolean wasGravity;
        boolean wasInvulnerable;
        boolean wasAllowFlight;
        boolean wasFlying;

        Session(Player target, AnimationType type, Runnable onFinish) {
            this.target = target;
            this.type = type;
            this.onFinish = onFinish;
        }

        void begin(String typeText, String reason) {
            origin = target.getLocation().clone();
            rise = computeRise();
            target.leaveVehicle();
            wasGravity = target.hasGravity();
            wasInvulnerable = target.isInvulnerable();
            wasAllowFlight = target.getAllowFlight();
            wasFlying = target.isFlying();
            target.setGravity(false);
            target.setInvulnerable(true);
            target.setAllowFlight(true);
            target.setFlying(true);
            target.setVelocity(new Vector(0, 0, 0));
            target.showTitle(Title.title(
                    Messages.color(msg.format("animation.title", "{type}", typeText, "{reason}", reason)),
                    Messages.color(msg.format("animation.subtitle", "{type}", typeText, "{reason}", reason)),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
            Sound start = switch (type) {
                case STORM -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
                case VORTEX -> Sound.ENTITY_EVOKER_PREPARE_SUMMON;
                case SILENCE -> Sound.ENTITY_WARDEN_SONIC_CHARGE;
                case WARNING -> Sound.BLOCK_NOTE_BLOCK_PLING;
                default -> Sound.BLOCK_BEACON_ACTIVATE;
            };
            origin.getWorld().playSound(origin, start, 1.0f, 1.0f);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }

        /** Ограничивает высоту подъёма потолком над игроком. */
        double computeRise() {
            double h = type.getHeight();
            if (h <= 0) return 0;
            World w = origin.getWorld();
            int bx = origin.getBlockX();
            int bz = origin.getBlockZ();
            int by = origin.getBlockY();
            double limit = h;
            for (int dy = 2; dy <= (int) Math.ceil(h) + 3; dy++) {
                if (!w.getBlockAt(bx, by + dy, bz).isPassable()) {
                    limit = Math.min(h, dy - 2.2);
                    break;
                }
            }
            return Math.max(0, limit);
        }

        void tick() {
            if (done) return;
            if (!target.isOnline() || target.isDead()) {
                finish(true, true);
                return;
            }
            double p = (double) tick / type.getTicks();
            double f = switch (type) {
                case ASCENSION -> p * p;
                case VORTEX -> p;
                case STORM -> Math.min(1.0, p / 0.35);
                case WARNING -> Math.sin(p * Math.PI);
                default -> 0.0;
            };
            float spin = switch (type) {
                case ASCENSION -> 5f;
                case VORTEX -> 20f;
                default -> 0f;
            };
            Location to = new Location(origin.getWorld(), origin.getX(), origin.getY() + f * rise, origin.getZ(),
                    origin.getYaw() + tick * spin, origin.getPitch());
            target.teleport(to);
            target.setVelocity(new Vector(0, 0, 0));
            target.setFallDistance(0f);
            effects(p, to);
            tick++;
            if (tick >= type.getTicks()) finish(false, true);
        }

        void effects(double p, Location at) {
            World w = at.getWorld();
            double x = at.getX();
            double y = at.getY();
            double z = at.getZ();
            switch (type) {
                case ASCENSION -> {
                    double phase = tick * 0.45;
                    double sweep = (tick * 0.12) % 2.0;
                    ring(w, x, y + sweep, z, 0.9, 6, phase, tick % 2 == 0 ? WHITE : RED);
                    ring(w, x, y + 1.9 - sweep, z, 0.9, 6, -phase, tick % 2 == 0 ? RED : WHITE);
                    w.spawnParticle(Particle.END_ROD, x, y + 0.2, z, 2, 0.25, 0.05, 0.25, 0.02);
                    if (tick % 2 == 0) {
                        ring(w, origin.getX(), origin.getY() + 0.1, origin.getZ(),
                                1.4 + Math.sin(tick * 0.25) * 0.3, 14, phase, RED);
                    }
                    for (double yy = origin.getY(); yy < y; yy += 0.6) {
                        w.spawnParticle(Particle.DUST, x, yy, z, 1, 0.05, 0, 0.05, 0, RED);
                    }
                    if (tick % 4 == 0) {
                        w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, (float) Math.min(2.0, 0.5 + p * 1.5));
                    }
                }
                case STORM -> {
                    if (tick % 8 == 4) {
                        double a = Math.random() * Math.PI * 2;
                        w.strikeLightningEffect(new Location(w, origin.getX() + Math.cos(a) * 4,
                                origin.getY(), origin.getZ() + Math.sin(a) * 4));
                    }
                    w.spawnParticle(Particle.ELECTRIC_SPARK, x, y + 1, z, 8, 0.5, 0.8, 0.5, 0.1);
                    if (tick % 2 == 0) ring(w, x, y + 0.1, z, 1.2, 10, tick * 0.3, RED);
                }
                case VORTEX -> {
                    for (int h = 0; h < 7; h++) {
                        double a = tick * 0.7 + h * 0.9;
                        double r = 0.35 + h * 0.22;
                        w.spawnParticle(Particle.DUST, x + Math.cos(a) * r, y - 0.3 + h * 0.45, z + Math.sin(a) * r,
                                1, 0, 0, 0, 0, h % 2 == 0 ? WHITE : RED);
                    }
                    w.spawnParticle(Particle.REVERSE_PORTAL, x, y + 1, z, 6, 0.4, 0.7, 0.4, 0.05);
                    if (tick % 5 == 0) w.playSound(at, Sound.ENTITY_PHANTOM_FLAP, 1.0f, (float) (0.6 + p));
                }
                case SILENCE -> {
                    double r = 2.0 * (1.0 - p) + 0.35;
                    ring(w, x, y + 1.7, z, r, 14, tick * 0.2, WHITE);
                    ring(w, x, y + 1.2, z, r, 14, -tick * 0.2, RED);
                    if (tick % 8 == 0) w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, (float) (0.5 + p));
                }
                case WARNING -> {
                    if (tick % 3 == 0) ring(w, x, y + 0.1, z, 0.5 + (tick % 12) * 0.12, 12, 0, RED);
                    w.spawnParticle(Particle.DUST, x, y + 2.2, z, 2, 0.15, 0.05, 0.15, 0, RED);
                    if (tick % 10 == 0) w.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                }
                default -> {
                }
            }
        }

        void finale(Location l) {
            World w = l.getWorld();
            switch (type) {
                case SILENCE -> {
                    w.spawnParticle(Particle.SONIC_BOOM, l.getX(), l.getY() + 1, l.getZ(), 1);
                    w.playSound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
                    burst(w, l);
                }
                case WARNING -> {
                    w.spawnParticle(Particle.CRIT, l.getX(), l.getY() + 1, l.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
                    w.playSound(l, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                }
                default -> {
                    w.spawnParticle(Particle.FLASH, l, 1);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
                    burst(w, l);
                    w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
                    w.strikeLightningEffect(l);
                }
            }
        }

        void finish(boolean quit, boolean runCallback) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            sessions.remove(target.getUniqueId());
            if (target.isOnline()) {
                if (!quit) finale(target.getLocation());
                target.setGravity(wasGravity);
                target.setInvulnerable(wasInvulnerable);
                target.setFlying(wasAllowFlight && wasFlying);
                target.setAllowFlight(wasAllowFlight);
                target.setFallDistance(0f);
                if (!target.isDead()) target.teleport(origin);
            }
            if (runCallback && onFinish != null) onFinish.run();
        }
    }
}
