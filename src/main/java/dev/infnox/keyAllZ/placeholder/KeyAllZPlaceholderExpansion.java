package dev.infnox.keyAllZ.placeholder;

import dev.infnox.keyAllZ.timer.Timer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PlaceholderAPI
 * Usage:
 *   %keyallz_timer_<name>%       – Full remaining time (e.g: 1d 2h 10m 5s)
 *   %keyallz_days_<name>%        – Remaining days
 *   %keyallz_hours_<name>%       – Remaining hours
 *   %keyallz_minutes_<name>%     – Remaining minutes
 *   %keyallz_secs_<name>%        – Remaining seconds
 *   %keyallz_seconds_<name>%     – Remaining seconds (alias)
 *   %keyallz_time_short_<name>%  – Short format (HH:mm:ss)
 */
public class KeyAllZPlaceholderExpansion extends PlaceholderExpansion {

    private final Map<String, Timer> timers;

    public KeyAllZPlaceholderExpansion(Map<String, Timer> timers) {
        this.timers = timers;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "keyallz";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Infnox";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String formatFull(long days, long hours, long minutes, long seconds) {
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private String formatShort(int seconds) {
        if (seconds <= 0) return "0s";
        int mins = seconds / 60;
        int secs = seconds % 60;
        if (mins > 0) {
            return mins + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    /**
     * Finds a timer by name
     */
    private Timer findTimer(String searchName) {
        String lowerSearch = searchName.toLowerCase();
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(lowerSearch) || entry.getValue().getName().equalsIgnoreCase(lowerSearch)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }

        String[] parts = identifier.split("_", 2);
        if (parts.length < 2) return null;

        String type = parts[0].toLowerCase();
        String name = parts[1];

        Timer timer = findTimer(name);
        if (timer == null || !timer.isRunning()) {
            return "Inactive";
        }

        int remaining = timer.getTimeRemaining();
        long days = TimeUnit.SECONDS.toDays(remaining);
        long hours = TimeUnit.SECONDS.toHours(remaining) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(remaining) % 60;
        long seconds = remaining % 60;

        switch (type) {
            case "timer":
                return formatFull(days, hours, minutes, seconds);

            case "days":
                return String.valueOf(days);

            case "hours":
                return String.valueOf(hours);

            case "minutes":
                return String.valueOf(minutes);

            case "secs":
            case "seconds":
                return String.valueOf(seconds);

            case "remaining":
            case "remainingtime":
            case "remaining_time":
                return formatShort(remaining);

            case "time":
                if (name.toLowerCase().startsWith("short_")) {
                    String realName = name.substring("short_".length());
                    Timer shortTimer = findTimer(realName);
                    if (shortTimer == null || !shortTimer.isRunning()) {
                        return "Inactive";
                    }
                    int shortRemaining = shortTimer.getTimeRemaining();
                    long d = TimeUnit.SECONDS.toDays(shortRemaining);
                    long h = TimeUnit.SECONDS.toHours(shortRemaining) % 24;
                    long m = TimeUnit.SECONDS.toMinutes(shortRemaining) % 60;
                    long s = shortRemaining % 60;
                    return String.format("%02d:%02d:%02d", (int)((d * 24) + h), (int)m, (int)s);
                }
                return null;

            default:
                return null;
        }
    }
}