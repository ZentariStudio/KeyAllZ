package dev.infnox.keyAllZ.redis;

import com.google.gson.Gson;
import dev.infnox.keyAllZ.KeyAllZ;
import dev.infnox.keyAllZ.config.KeyAllDefinition;
import dev.infnox.keyAllZ.timer.Timer;
import org.bukkit.Bukkit;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;


public class RedisTimerSync implements TimerSyncListener {

    private static final int LEADER_TTL  = 15;
    private static final int CLAIM_TTL   = 60;

    private final KeyAllZ plugin;
    private final RedisManager redis;
    private final String serverId;
    private final String prefix;
    private final String channel;
    private final Gson gson = new Gson();

    public RedisTimerSync(KeyAllZ plugin, RedisManager redis, String serverId, String prefix) {
        this.plugin   = plugin;
        this.redis    = redis;
        this.serverId = serverId;
        this.prefix   = prefix;
        this.channel  = prefix + ":events";

        redis.subscribe(channel, this::onMessage);
    }


    @Override
    public void onTimerStarted(String defName, int total, int remaining, boolean looping, int reminderInterval, String cycleId) {
        redis.setNx(leaderKey(defName), serverId, LEADER_TTL);
        saveState(defName, total, remaining, looping, reminderInterval, cycleId);
        redis.sadd(activeKey(), defName);

        SyncEvent ev = new SyncEvent();
        ev.type             = SyncEvent.TYPE_START;
        ev.serverId         = serverId;
        ev.name             = defName;
        ev.totalSeconds     = total;
        ev.remainingSeconds = remaining;
        ev.looping          = looping;
        ev.reminderInterval = reminderInterval;
        ev.cycleId          = cycleId;
        publish(ev);
    }

    @Override
    public void onTimerStopped(String defName) {
        cleanupRedis(defName);

        SyncEvent ev = new SyncEvent();
        ev.type     = SyncEvent.TYPE_STOP;
        ev.serverId = serverId;
        ev.name     = defName;
        publish(ev);
    }

    @Override
    public void onTimerEnded(String defName) {

        cleanupRedis(defName);

        SyncEvent ev = new SyncEvent();
        ev.type     = SyncEvent.TYPE_END;
        ev.serverId = serverId;
        ev.name     = defName;
        publish(ev);

        plugin.getTimers().remove(defName.toLowerCase(Locale.ROOT));
    }

    @Override
    public void onTimerCycleReset(String defName, int total, String cycleId) {

        redis.hset(stateKey(defName), Map.of(
                "cycleId",          cycleId,
                "remainingSeconds", String.valueOf(total),
                "lastTickAt",       String.valueOf(System.currentTimeMillis())
        ));

        SyncEvent ev = new SyncEvent();
        ev.type         = SyncEvent.TYPE_CYCLE_RESET;
        ev.serverId     = serverId;
        ev.name         = defName;
        ev.totalSeconds = total;
        ev.cycleId      = cycleId;
        publish(ev);
    }

    @Override
    public void onTimerTick(String defName, int remaining, int total, String cycleId) {
        boolean stillLeader = redis.renew(leaderKey(defName), serverId, LEADER_TTL);
        if (!stillLeader) {

            stillLeader = redis.setNx(leaderKey(defName), serverId, LEADER_TTL);
        }
        if (!stillLeader) return;

        redis.hset(stateKey(defName), Map.of(
                "remainingSeconds", String.valueOf(remaining),
                "lastTickAt",       String.valueOf(System.currentTimeMillis())
        ));

        SyncEvent ev = new SyncEvent();
        ev.type             = SyncEvent.TYPE_TICK;
        ev.serverId         = serverId;
        ev.name             = defName;
        ev.remainingSeconds = remaining;
        ev.totalSeconds     = total;
        ev.cycleId          = cycleId;
        publish(ev);
    }

    @Override
    public boolean claimGlobalCommands(String defName, String cycleId) {
        return redis.setNx(globalKey(defName, cycleId), serverId, CLAIM_TTL);
    }

    @Override
    public List<Player> filterClaimedPlayers(String defName, String cycleId, List<Player> players) {
        if (players.isEmpty()) return players;

        List<String> keys = new ArrayList<>(players.size());
        for (Player p : players) {
            keys.add(playerKey(defName, cycleId, p.getUniqueId().toString()));
        }

        List<Boolean> results = redis.setNxBatch(keys, serverId, CLAIM_TTL);

        List<Player> claimed = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (results.get(i)) claimed.add(players.get(i));
        }
        return claimed;
    }


    public void publishLoopSet(String defName, boolean looping) {
        redis.hset(stateKey(defName), Map.of("looping", String.valueOf(looping)));

        SyncEvent ev = new SyncEvent();
        ev.type      = SyncEvent.TYPE_LOOP_SET;
        ev.serverId  = serverId;
        ev.name      = defName;
        ev.loopValue = looping;
        publish(ev);
    }

    public void publishRemindSet(String defName, int interval) {
        redis.hset(stateKey(defName), Map.of("reminderInterval", String.valueOf(interval)));

        SyncEvent ev = new SyncEvent();
        ev.type        = SyncEvent.TYPE_REMIND_SET;
        ev.serverId    = serverId;
        ev.name        = defName;
        ev.remindValue = interval;
        publish(ev);
    }

    public void publishReload() {
        SyncEvent ev = new SyncEvent();
        ev.type     = SyncEvent.TYPE_RELOAD;
        ev.serverId = serverId;
        publish(ev);
    }

    public boolean loadTimersFromRedis() {
        Set<String> active = redis.smembers(activeKey());
        if (active.isEmpty()) return false;

        int restored = 0;
        long now = System.currentTimeMillis();

        for (String defName : active) {
            Map<String, String> state = redis.hgetAll(stateKey(defName));
            if (state.isEmpty() || !Boolean.parseBoolean(state.getOrDefault("running", "false"))) {
                redis.srem(activeKey(), defName);
                continue;
            }

            String name = state.getOrDefault("definition", defName);
            KeyAllDefinition def = plugin.getConfigManager().getKeyAll(name);
            if (def == null) {
                plugin.getLogger().warning("[Redis] Skipping timer '" + name + "': definition not found in config.");
                cleanupRedis(defName);
                continue;
            }

            int total       = parseInt(state.get("totalSeconds"), 0);
            int remaining   = parseInt(state.get("remainingSeconds"), total);
            boolean looping = Boolean.parseBoolean(state.getOrDefault("looping", "false"));
            int reminder    = parseInt(state.get("reminderInterval"), def.getReminder() != null ? def.getReminder().getInterval() : 10);
            String cycleId  = state.getOrDefault("cycleId", UUID.randomUUID().toString());
            long lastTickAt = parseLong(state.get("lastTickAt"), now);

            long elapsed = Math.max(0, (now - lastTickAt) / 1000L);
            remaining = adjustForDowntime(remaining, total, looping, elapsed);
            if (remaining < 0) {
                cleanupRedis(defName);
                continue;
            }

            Timer timer = new Timer(plugin, def, total, remaining, plugin.getRewardExecutor());
            timer.setLooping(looping);
            timer.setReminderInterval(reminder);
            timer.setSyncListener(this);
            redis.setNx(leaderKey(defName), serverId, LEADER_TTL);
            timer.startReplicated(remaining, cycleId);
            plugin.getTimers().put(defName.toLowerCase(Locale.ROOT), timer);
            restored++;
        }

        if (restored > 0) plugin.getLogger().info("[Redis] Restored " + restored + " timer(s).");
        return restored > 0;
    }


    private void onMessage(String json) {
        SyncEvent ev;
        try {
            ev = gson.fromJson(json, SyncEvent.class);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[KeyAllZ-Redis] Malformed event ignored: " + json);
            return;
        }

        if (ev == null || ev.type == null) return;
        if (serverId.equals(ev.serverId)) return; // own event, already handled locally
        if (!plugin.isEnabled()) return; // don't schedule after the plugin has begun shutting down

        Bukkit.getGlobalRegionScheduler().run(plugin, t -> dispatch(ev));
    }

    private void dispatch(SyncEvent ev) {
        switch (ev.type) {
            case SyncEvent.TYPE_START       -> onRemoteStart(ev);
            case SyncEvent.TYPE_STOP        -> onRemoteStop(ev);
            case SyncEvent.TYPE_TICK        -> onRemoteTick(ev);
            case SyncEvent.TYPE_END         -> onRemoteEnd(ev);
            case SyncEvent.TYPE_CYCLE_RESET -> onRemoteCycleReset(ev);
            case SyncEvent.TYPE_LOOP_SET    -> onRemoteLoopSet(ev);
            case SyncEvent.TYPE_REMIND_SET  -> onRemoteRemindSet(ev);
            case SyncEvent.TYPE_RELOAD      -> onRemoteReload(ev);
        }
    }

    private void onRemoteStart(SyncEvent ev) {
        String key = ev.name.toLowerCase(Locale.ROOT);
        Timer old = plugin.getTimers().get(key);
        if (old != null) old.stopReplicated();

        KeyAllDefinition def = plugin.getConfigManager().getKeyAll(ev.name);
        if (def == null) {
            plugin.getLogger().warning("[Redis] Received START for unknown definition '" + ev.name + "' — does this server have the same config?");
            return;
        }

        Timer timer = new Timer(plugin, def, ev.totalSeconds, ev.remainingSeconds, plugin.getRewardExecutor());
        timer.setLooping(ev.looping);
        timer.setReminderInterval(ev.reminderInterval);
        timer.setSyncListener(this);
        timer.startReplicated(ev.remainingSeconds, ev.cycleId);
        plugin.getTimers().put(key, timer);
    }

    private void onRemoteStop(SyncEvent ev) {
        Timer timer = plugin.getTimers().remove(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) timer.stopReplicated();
    }

    private void onRemoteTick(SyncEvent ev) {
        Timer timer = plugin.getTimers().get(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) timer.syncRemainingTime(ev.remainingSeconds, ev.cycleId);
    }

    private void onRemoteEnd(SyncEvent ev) {
        Timer timer = plugin.getTimers().remove(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) {
            timer.completeFromRemote();
        }
    }

    private void onRemoteCycleReset(SyncEvent ev) {
        Timer timer = plugin.getTimers().get(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) {
            timer.cycleResetFromRemote(ev.totalSeconds, ev.cycleId);
        }
    }

    private void onRemoteLoopSet(SyncEvent ev) {
        Timer timer = plugin.getTimers().get(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) timer.setLooping(ev.loopValue);
    }

    private void onRemoteRemindSet(SyncEvent ev) {
        Timer timer = plugin.getTimers().get(ev.name.toLowerCase(Locale.ROOT));
        if (timer != null) timer.setReminderInterval(ev.remindValue);
    }

    private void onRemoteReload(SyncEvent ev) {
        plugin.getConfigManager().reload();

        Map<String, Timer> timers = plugin.getTimers();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            KeyAllDefinition newDef = plugin.getConfigManager().getKeyAll(entry.getKey());
            if (newDef == null) {
                entry.getValue().stopReplicated();
                toRemove.add(entry.getKey());
            } else {
                entry.getValue().setDefinition(newDef);
            }
        }
        toRemove.forEach(timers::remove);

        plugin.getLogger().info("[Redis] Config reloaded remotely by " + ev.serverId + ". Active timers updated.");
    }


    private void cleanupRedis(String defName) {
        redis.del(stateKey(defName));
        redis.del(leaderKey(defName));
        redis.srem(activeKey(), defName);
    }

    private void saveState(String defName, int total, int remaining, boolean looping, int reminderInterval, String cycleId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("definition",       defName);
        fields.put("totalSeconds",     String.valueOf(total));
        fields.put("remainingSeconds", String.valueOf(remaining));
        fields.put("looping",          String.valueOf(looping));
        fields.put("reminderInterval", String.valueOf(reminderInterval));
        fields.put("cycleId",          cycleId);
        fields.put("running",          "true");
        fields.put("lastTickAt",       String.valueOf(System.currentTimeMillis()));
        redis.hset(stateKey(defName), fields);
    }

    private void publish(SyncEvent ev) {
        try {
            redis.publish(channel, gson.toJson(ev));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[KeyAllZ-Redis] Failed to publish event: " + e.getMessage());
        }
    }

    private String stateKey(String name)  { return prefix + ":timer:"  + name.toLowerCase(Locale.ROOT); }
    private String leaderKey(String name) { return prefix + ":leader:"  + name.toLowerCase(Locale.ROOT); }
    private String activeKey()            { return prefix + ":timers"; }

    private String globalKey(String name, String cycleId) {
        return prefix + ":global:" + name.toLowerCase(Locale.ROOT) + ":" + cycleId;
    }

    private String playerKey(String defName, String cycleId, String uuid) {
        return prefix + ":exec:" + defName.toLowerCase(Locale.ROOT) + ":" + cycleId + ":" + uuid;
    }

    private int adjustForDowntime(int remaining, int total, boolean looping, long elapsed) {
        if (total <= 0) return -1;
        int norm = Math.max(0, Math.min(remaining, total));
        long after = (long) norm - elapsed;
        if (after > 0) return (int) after;
        if (!looping) return -1;
        long overdue = -after;
        long into = overdue % total;
        return into == 0 ? total : (int) (total - into);
    }

    private int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }
}
