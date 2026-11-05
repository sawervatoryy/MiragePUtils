package org.example.sawervatoryy.miragePUtils;

import java.util.UUID;

/** Запись журнала наказаний, выданных через MiragePUtils. */
public final class Punishment {
    public int id;
    public PunishType type;
    public UUID targetUuid;
    public String targetName;
    public UUID staffUuid;
    public String staffName;
    public String reason;
    public String category = "manual";
    public long created;
    /** Длительность в мс, -1 = навсегда. */
    public long duration = -1;
    public boolean silent;
    public String animation;
    /** Кто сбросил запись из лесенки (null — запись учитывается). */
    public String resetBy;

    public boolean isPermanent() {
        return duration < 0;
    }
}
