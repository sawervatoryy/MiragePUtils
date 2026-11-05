package org.example.sawervatoryy.miragePUtils;

import java.util.Locale;

/** Типы наказаний MiragePUtils. */
public enum PunishType {
    BAN("Бан", true),
    IPBAN("IP-бан", true),
    MUTE("Мут", true),
    WARN("Предупреждение", false),
    KICK("Кик", false);

    private final String displayName;
    private final boolean timed;

    PunishType(String displayName, boolean timed) {
        this.displayName = displayName;
        this.timed = timed;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** true, если наказание по умолчанию бессрочное и срок задаётся вручную (бан/мут). */
    public boolean isTimed() {
        return timed;
    }

    public static PunishType parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
