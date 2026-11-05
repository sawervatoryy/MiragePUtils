package org.example.sawervatoryy.miragePUtils;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * MiragePUtils — надстройка над LiteBans для Paper 1.21.4+: умная лесенка наказаний, GUI и анимации.
 * Студия: MirageStudio | Автор: sawervatoryy | Discord: https://discord.gg/dWPenZegK
 */
public final class MiragePUtils extends JavaPlugin {

    private static final List<String> COMMANDS = List.of("punish", "phistory", "mirageputils");

    private Messages messages;
    private Database database;
    private PunishManager manager;
    private Animations animations;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.load();
        applyTimeSettings();

        database = new Database(this);
        try {
            database.init();
        } catch (Exception ex) {
            getLogger().severe("Не удалось подключиться к базе данных: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(this);
        animations = new Animations(this, messages);
        manager = new PunishManager(this, database, messages, webhook, animations);
        Menus menus = new Menus(manager, messages);

        getServer().getPluginManager().registerEvents(new AnimationListener(animations), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, manager, menus), this);

        Commands commands = new Commands(this, manager, messages, menus, animations);
        for (String name : COMMANDS) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) continue;
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("MiragePUtils " + getPluginMeta().getVersion() + " включён.");
        getLogger().info("MirageStudio | Автор: sawervatoryy | Discord: https://discord.gg/dWPenZegK");
    }

    @Override
    public void onDisable() {
        if (animations != null) animations.shutdown();
        if (manager != null) manager.shutdown();
        if (database != null) database.close();
    }

    /** Перезагрузка config.yml, messages.yml и шаблонов причин. */
    public void reloadAll() {
        reloadConfig();
        messages.load();
        applyTimeSettings();
        manager.reload();
    }

    private void applyTimeSettings() {
        TimeUtil.configure(getConfig().getString("settings.date-format", "dd.MM.yyyy HH:mm"),
                getConfig().getString("settings.timezone", "system"),
                messages.raw("words.permanent"));
    }
}
