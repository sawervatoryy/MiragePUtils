package org.example.sawervatoryy.miragePUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор и форматирование сроков: 30m, 12h, 7d, 1w, 1mo, 1y, 1d12h. */
public final class TimeUtil {

    public static final long PERMANENT = -1L;
    public static final long INVALID = -2L;

    private static final Pattern FULL = Pattern.compile("^(?:\\d{1,6}(?:mo|y|w|d|h|m|s))+$");
    private static final Pattern PART = Pattern.compile("(\\d{1,6})(mo|y|w|d|h|m|s)");

    private static DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static ZoneId zone = ZoneId.systemDefault();
    private static String permText = "навсегда";

    private TimeUtil() {
    }

    public static void configure(String pattern, String zoneId, String permanentText) {
        try {
            dateFormat = DateTimeFormatter.ofPattern(pattern);
        } catch (Exception ex) {
            dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        }
        try {
            zone = zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("system")
                    ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        } catch (Exception ex) {
            zone = ZoneId.systemDefault();
        }
        if (permanentText != null) permText = permanentText;
    }

    public static String permanentText() {
        return permText;
    }

    /** @return миллисекунды, {@link #PERMANENT} или {@link #INVALID}. */
    public static long parse(String input) {
        if (input == null) return INVALID;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever") || s.equals("навсегда")) {
            return PERMANENT;
        }
        if (!FULL.matcher(s).matches()) return INVALID;
        Matcher m = PART.matcher(s);
        long total = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 7 * 86_400_000L;
                case "mo" -> 30 * 86_400_000L;
                default -> 365 * 86_400_000L;
            };
            total += n * unit;
        }
        return total > 0 ? total : INVALID;
    }

    /** Человекочитаемая длительность: «2д 3ч 15мин». */
    public static String format(long ms) {
        if (ms < 0) return permText;
        long sec = ms / 1000;
        long d = sec / 86_400;
        long h = (sec % 86_400) / 3_600;
        long m = (sec % 3_600) / 60;
        long s = sec % 60;
        List<String> parts = new ArrayList<>();
        if (d > 0) parts.add(d + "д");
        if (h > 0) parts.add(h + "ч");
        if (m > 0) parts.add(m + "мин");
        if (parts.isEmpty()) parts.add(s + "сек");
        return String.join(" ", parts.subList(0, Math.min(3, parts.size())));
    }

    /** Срок в формате, понятном LiteBans: 30d, 12h, 45m, 30s. */
    public static String litebans(long ms) {
        if (ms <= 0) return "1d";
        if (ms % 86_400_000L == 0) return (ms / 86_400_000L) + "d";
        if (ms % 3_600_000L == 0) return (ms / 3_600_000L) + "h";
        if (ms % 60_000L == 0) return (ms / 60_000L) + "m";
        return Math.max(1L, ms / 1000L) + "s";
    }

    public static String date(long millis) {
        return dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone));
    }
}
