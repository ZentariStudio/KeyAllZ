package dev.infnox.keyAllZ.redis;

public class SyncEvent {
    public static final String TYPE_START        = "START";
    public static final String TYPE_STOP         = "STOP";
    public static final String TYPE_TICK         = "TICK";
    public static final String TYPE_END          = "END";
    public static final String TYPE_CYCLE_RESET  = "CYCLE_RESET";
    public static final String TYPE_LOOP_SET     = "LOOP_SET";
    public static final String TYPE_REMIND_SET   = "REMIND_SET";
    public static final String TYPE_RELOAD       = "RELOAD";

    public String type;
    public String serverId;
    public String name;

    // START / TICK / CYCLE_RESET
    public int    totalSeconds;
    public int    remainingSeconds;
    public String cycleId;

    // START
    public boolean looping;
    public int     reminderInterval;

    // LOOP_SET
    public boolean loopValue;

    // REMIND_SET
    public int remindValue;
}
