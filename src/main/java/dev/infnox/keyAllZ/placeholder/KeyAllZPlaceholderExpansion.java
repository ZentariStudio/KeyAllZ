package dev.infnox.keyAllZ.placeholder;

import dev.infnox.keyAllZ.KeyAllZ;
import dev.infnox.keyAllZ.timer.Timer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PlaceholderAPI Expansion for KeyAllZ.
 * Identifiers:
 * %keyallz_timer_<name>%         – Full format (e.g. 1d 2h 10m 5s)
 * %keyallz_time_short_<name>%    – Digital format (HH:mm:ss or mm:ss)
 * %keyallz_days_<name>%          – Days remaining
 * %keyallz_hours_<name>%         – Hours remaining (0-23)
 * %keyallz_minutes_<name>%       – Minutes remaining (0-59)
 * %keyallz_seconds_<name>%       – Seconds remaining (0-59)
 * %keyallz_total_seconds_<name>% – Total seconds remaining (raw number)
 */
public class KeyAllZPlaceholderExpansion extends PlaceholderExpansion {

    private final KeyAllZ plugin;
    private final Map<String, Timer> timers;

    public KeyAllZPlaceholderExpansion(KeyAllZ plugin) {
        this.plugin = plugin;
        this.timers = plugin.getTimers();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "keyallz";
    }

    @Override
    public @NotNull String getAuthor() {
        return "luvtoxic";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.isEmpty()) return null;

        if (params.startsWith("time_short_")) {
            String timerName = params.substring(11);
            return formatDigital(getTimer(timerName));
        }

        String[] parts = params.split("_", 2);
        if (parts.length < 2) return null;

        String type = parts[0].toLowerCase();
        String name = parts[1];

        if (type.equals("total") && name.startsWith("seconds_")) {
            return String.valueOf(getRemainingSeconds(name.substring(8)));
        }

        Timer timer = getTimer(name);


        if (timer == null || !timer.isRunning()) {
            return null;
        }

        long totalSeconds = timer.getTimeRemaining();
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        switch (type) {
            case "timer":
                return formatFull(days, hours, minutes, seconds);
            case "days":
                return String.valueOf(days);
            case "hours":
                return String.valueOf(hours);
            case "minutes":
                return String.valueOf(minutes);
            case "seconds":
            case "secs":
                return String.valueOf(seconds);
            case "remaining":
                return formatSmart(totalSeconds);
            default:
                return null;
        }
    }


    private @Nullable Timer getTimer(String name) {

        Timer timer = timers.get(name);
        if (timer != null) return timer;

        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long getRemainingSeconds(String name) {
        Timer t = getTimer(name);
        return (t != null && t.isRunning()) ? t.getTimeRemaining() : 0;
    }

    /**
     *  Formats as "1d 2h 10m 5s", omitting zero values.
     */
    private String formatFull(long d, long h, long m, long s) {
        if (d == 0 && h == 0 && m == 0 && s == 0) return "0s";

        StringBuilder sb = new StringBuilder(16);
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s"); // Show seconds if everything else is 0 or seconds > 0

        return sb.toString().trim();
    }

    /**
     *  Formats as "HH:mm:ss" or "mm:ss".
     */
    private String formatDigital(Timer timer) {
        if (timer == null || !timer.isRunning()) return "00:00";

        long totalSeconds = timer.getTimeRemaining();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder(8);

        // HH:
        if (hours > 0) {
            if (hours < 10) sb.append('0');
            sb.append(hours).append(':');
        }

        // mm:
        if (minutes < 10) sb.append('0');
        sb.append(minutes).append(':');

        // ss
        if (seconds < 10) sb.append('0');
        sb.append(seconds);

        return sb.toString();
    }

    /**
     * Formats like "5m 30s" or just "30s".
     */
    private String formatSmart(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;

        if (mins > 0) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }
}