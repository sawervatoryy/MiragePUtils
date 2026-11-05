package org.example.sawervatoryy.miragePUtils;

import java.util.Locale;

/** Анимации наказания: игрока поднимает в воздух, затем происходит финал. */
public enum AnimationType {
    ASCENSION("Взлёт", 70, 6.0),
    STORM("Шторм", 60, 2.5),
    VORTEX("Вихрь", 70, 4.0),
    SILENCE("Тишина", 40, 0.0),
    WARNING("Вспышка", 30, 1.2),
    NONE("Без анимации", 0, 0.0);

    private final String displayName;
    private final int ticks;
    private final double height;

    AnimationType(String displayName, int ticks, double height) {
        this.displayName = displayName;
        this.ticks = ticks;
        this.height = height;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTicks() {
        return ticks;
    }

    public double getHeight() {
        return height;
    }

    public static AnimationType parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static AnimationType defaultFor(PunishType type) {
        return switch (type) {
            case BAN -> ASCENSION;
            case IPBAN -> VORTEX;
            case MUTE -> SILENCE;
            case WARN -> WARNING;
            case KICK -> STORM;
        };
    }
}
