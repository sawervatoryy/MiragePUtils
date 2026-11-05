package org.example.sawervatoryy.miragePUtils;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Логирование наказаний в Discord через webhook (асинхронно, без внешних библиотек). */
public final class DiscordWebhook {

    public static final int COLOR_RED = 0xE74C3C;
    public static final int COLOR_WHITE = 0xFFFFFF;

    private final MiragePUtils plugin;
    private final HttpClient client = HttpClient.newHttpClient();

    public DiscordWebhook(MiragePUtils plugin) {
        this.plugin = plugin;
    }

    public void send(String title, String description, int color) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("webhook.enabled", false)) return;
        String url = c.getString("webhook.url", "");
        if (url == null || url.isBlank() || url.contains("YOUR_WEBHOOK")) return;
        String json = "{\"username\":\"" + esc(c.getString("webhook.username", "MiragePUtils")) + "\","
                + "\"embeds\":[{\"title\":\"" + esc(title) + "\","
                + "\"description\":\"" + esc(description) + "\","
                + "\"color\":" + color + ","
                + "\"footer\":{\"text\":\"MirageStudio • MiragePUtils\"},"
                + "\"timestamp\":\"" + Instant.now() + "\"}]}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(ex -> {
                plugin.getLogger().warning("Discord webhook: " + ex.getMessage());
                return null;
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Discord webhook: " + ex.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
}
