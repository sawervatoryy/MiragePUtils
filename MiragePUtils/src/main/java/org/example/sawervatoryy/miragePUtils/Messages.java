package org.example.sawervatoryy.miragePUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Загрузка messages.yml и работа с цветами (&amp;f / &amp;c, поддерживается &amp;#rrggbb). */
public final class Messages {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final MiragePUtils plugin;
    private FileConfiguration cfg;

    public Messages(MiragePUtils plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // дефолты не критичны
        }
    }

    // ---------- статические утилиты ----------

    public static Component color(String text) {
        return SERIALIZER.deserialize(text == null ? "" : text);
    }

    /** Для названий и описаний предметов: без курсива по умолчанию. */
    public static Component item(String text) {
        return color(text).decoration(TextDecoration.ITALIC, false);
    }

    public static String strip(String text) {
        return PlainTextComponentSerializer.plainText().serialize(color(text));
    }

    /** Подстановка пар {ключ}, значение. */
    public static String apply(String text, Object... kv) {
        String out = text == null ? "" : text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return out;
    }

    // ---------- доступ к сообщениям ----------

    public String raw(String key) {
        String s = cfg.getString(key);
        return s == null ? "&cMissing message: " + key : s;
    }

    public List<String> list(String key) {
        List<String> l = cfg.getStringList(key);
        if (l.isEmpty()) {
            String single = cfg.getString(key);
            if (single != null) return new ArrayList<>(List.of(single));
        }
        return l;
    }

    public String format(String key, Object... kv) {
        return Messages.apply(raw(key).replace("{prefix}", raw("prefix")), kv);
    }

    public List<String> formatList(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        String prefix = raw("prefix");
        for (String line : list(key)) {
            out.add(Messages.apply(line.replace("{prefix}", prefix), kv));
        }
        return out;
    }

    public Component component(String key, Object... kv) {
        return color(format(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(component(key, kv));
    }

    public void sendList(CommandSender to, String key, Object... kv) {
        for (String line : formatList(key, kv)) to.sendMessage(color(line));
    }
}
