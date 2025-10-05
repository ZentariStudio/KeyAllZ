package dev.infnox.keyAllZ.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;

public class KeyAllDefinition {

    private final String name;
    private final String title;
    private final String actionbar;
    private final String chatMessage;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;
    private final List<String> playerCommands;
    private final List<String> consoleCommands;
    private final String permission;
    private final ReminderDefinition reminder;

    public KeyAllDefinition(String name, String title, String actionbar, String chatMessage,
                            String sound, float soundVolume, float soundPitch,
                            List<String> playerCommands, List<String> consoleCommands,
                            String permission, ReminderDefinition reminder) {
        this.name = name;
        this.title = title;
        this.actionbar = actionbar;
        this.chatMessage = chatMessage;
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.playerCommands = playerCommands;
        this.consoleCommands = consoleCommands;
        this.permission = permission;
        this.reminder = reminder;
    }

    public static KeyAllDefinition fromConfig(String name, ConfigurationSection sec) {
        ReminderDefinition reminder = null;
        if (sec.isConfigurationSection("reminders")) {
            reminder = ReminderDefinition.fromConfig(sec.getConfigurationSection("reminders"));
        }

        return new KeyAllDefinition(
                name,
                sec.getString("title", ""),
                sec.getString("actionbar", ""),
                sec.getString("chat", ""),
                sec.getString("sound", ""),
                (float) sec.getDouble("sound-volume", 1.0),
                (float) sec.getDouble("sound-pitch", 1.0),
                sec.getStringList("player-commands"),
                sec.getStringList("console-commands"),
                sec.getString("permission", ""),
                reminder
        );
    }

    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getActionbar() { return actionbar; }
    public String getChatMessage() { return chatMessage; }
    public String getSound() { return sound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public List<String> getPlayerCommands() { return Collections.unmodifiableList(playerCommands); }
    public List<String> getConsoleCommands() { return Collections.unmodifiableList(consoleCommands); }
    public String getPermission() { return permission; }
    public ReminderDefinition getReminder() { return reminder; }

    /**
     * Inner class for reminder configuration.
     */
    public static class ReminderDefinition {
        private final int interval;
        private final String chat;
        private final String title;
        private final String actionbar;
        private final String sound;
        private final float soundVolume;
        private final float soundPitch;

        public ReminderDefinition(int interval, String chat, String title, String actionbar,
                                  String sound, float soundVolume, float soundPitch) {
            this.interval = interval;
            this.chat = chat;
            this.title = title;
            this.actionbar = actionbar;
            this.sound = sound;
            this.soundVolume = soundVolume;
            this.soundPitch = soundPitch;
        }

        public static ReminderDefinition fromConfig(ConfigurationSection sec) {
            return new ReminderDefinition(
                    sec.getInt("interval", 10),
                    sec.getString("chat", ""),
                    sec.getString("title", ""),
                    sec.getString("actionbar", ""),
                    sec.getString("sound", ""),
                    (float) sec.getDouble("sound-volume", 1.0),
                    (float) sec.getDouble("sound-pitch", 1.0)
            );
        }

        public int getInterval() { return interval; }
        public String getChat() { return chat; }
        public String getTitle() { return title; }
        public String getActionbar() { return actionbar; }
        public String getSound() { return sound; }
        public float getSoundVolume() { return soundVolume; }
        public float getSoundPitch() { return soundPitch; }
    }
}
