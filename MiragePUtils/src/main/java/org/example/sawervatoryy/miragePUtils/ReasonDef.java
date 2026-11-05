package org.example.sawervatoryy.miragePUtils;

import org.bukkit.Material;

import java.util.List;

/** Шаблон причины для /punish: лесенка наказаний по числу прошлых нарушений. */
public final class ReasonDef {

    public record Step(PunishType type, long duration) {
    }

    public final String id;
    public final String name;
    public final Material icon;
    public final List<String> description;
    public final String permission;
    public final String animation;
    public final List<Step> ladder;

    public ReasonDef(String id, String name, Material icon, List<String> description,
                     String permission, String animation, List<Step> ladder) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.permission = permission;
        this.animation = animation;
        this.ladder = ladder;
    }

    public Step stepFor(int previousCount) {
        return ladder.get(Math.min(Math.max(previousCount, 0), ladder.size() - 1));
    }
}
