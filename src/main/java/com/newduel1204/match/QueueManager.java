package com.newduel1204.match;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匹配队列。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有比较都在内存中完成，队列扫描间隔可配置（默认 1 秒），不占用 tick。</li>
 *   <li>ELO 搜索范围随等待时间线性扩大，避免高分段玩家永远匹配不到人。</li>
 *   <li>匹配成功后立刻从队列移除，由 {@link MatchManager} 负责占场与快照。</li>
 * </ul>
 */
public final class QueueManager {

    private final NewDuel1204 plugin;
    private final Map<UUID, QueueEntry> queue = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int tickCounter;

    public QueueManager(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        int interval = plugin.settings().queueIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
    }

    /* ------------------------------------------------------------------- join */

    public boolean join(Player player, GameMode mode, boolean ranked) {
        UUID uuid = player.getUniqueId();
        if (queue.containsKey(uuid)) {
            plugin.messages().send(player, "queue.already-queued");
            return false;
        }
        if (plugin.matches().inMatch(player) || plugin.matches().isSpectator(player)) {
            plugin.messages().send(player, "duel.busy");
            return false;
        }
        if (ranked) {
            if (!plugin.settings().rankedEnabled()) {
                plugin.messages().send(player, "queue.ranked-disabled");
                return false;
            }
            if (!player.hasPermission("newduel.ranked")) {
                plugin.messages().send(player, "general.no-permission");
                return false;
            }
            int minLevel = plugin.settings().rankedMinLevel();
            if (minLevel > 0 && player.getLevel() < minLevel) {
                plugin.messages().send(player, "general.disabled");
                return false;
            }
        }

        int elo = ranked
                ? plugin.profiles().get(player).modeElo(mode.id(), plugin.settings().startElo())
                : 0;
        QueueEntry entry = new QueueEntry(uuid, player.getName(), mode, ranked, System.currentTimeMillis(), elo);
        queue.put(uuid, entry);
        plugin.messages().send(player, "queue.joined",
                "mode", mode.display(), "type", ranked ? "排位" : "普通",
                "size", String.valueOf(countOf(mode, ranked)));
        return true;
    }

    public void leave(UUID uuid, boolean notify) {
        QueueEntry removed = queue.remove(uuid);
        if (removed != null && notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, "queue.left");
            }
        }
    }

    public boolean inQueue(UUID uuid) {
        return queue.containsKey(uuid);
    }

    public QueueEntry entry(UUID uuid) {
        return queue.get(uuid);
    }

    public int size() {
        return queue.size();
    }

    public int countOf(GameMode mode, boolean ranked) {
        int count = 0;
        for (QueueEntry entry : queue.values()) {
            if (entry.mode().equals(mode) && entry.ranked() == ranked) {
                count++;
            }
        }
        return count;
    }

    /* ------------------------------------------------------------------- tick */

    private void tick() {
        tickCounter++;
        if (queue.isEmpty()) {
            return;
        }
        // 清理已经离线的玩家
        for (QueueEntry entry : new ArrayList<>(queue.values())) {
            if (Bukkit.getPlayer(entry.uuid()) == null) {
                queue.remove(entry.uuid());
            }
        }
        if (queue.size() < 2) {
            pushStatus();
            return;
        }

        Map<String, List<QueueEntry>> groups = new HashMap<>();
        for (QueueEntry entry : queue.values()) {
            String key = entry.mode().id() + (entry.ranked() ? ":ranked" : ":normal");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        for (List<QueueEntry> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparingLong(QueueEntry::joinedAt));
            if (matchGroup(group)) {
                return;
            }
        }
        pushStatus();
    }

    private boolean matchGroup(List<QueueEntry> group) {
        for (int i = 0; i < group.size(); i++) {
            QueueEntry first = group.get(i);
            if (!queue.containsKey(first.uuid())) {
                continue;
            }
            for (int j = i + 1; j < group.size(); j++) {
                QueueEntry second = group.get(j);
                if (!queue.containsKey(second.uuid())) {
                    continue;
                }
                if (!withinRange(first, second)) {
                    continue;
                }
                Player a = Bukkit.getPlayer(first.uuid());
                Player b = Bukkit.getPlayer(second.uuid());
                if (a == null || b == null) {
                    continue;
                }
                queue.remove(first.uuid());
                queue.remove(second.uuid());
                plugin.messages().send(a, "queue.matched", "player", b.getName(), "mode", first.mode().display());
                plugin.messages().send(b, "queue.matched", "player", a.getName(), "mode", first.mode().display());
                plugin.matches().requestStart(a, b, first.mode(), first.ranked());
                return true;
            }
        }
        return false;
    }

    /** 等待越久，可接受的 ELO 差距越大。 */
    private boolean withinRange(QueueEntry a, QueueEntry b) {
        if (!a.ranked()) {
            return true;
        }
        int waited = (int) ((System.currentTimeMillis() - Math.min(a.joinedAt(), b.joinedAt())) / 1000L);
        int range = Math.min(plugin.settings().maxEloRange(),
                plugin.settings().initialEloRange() + (waited / 10) * plugin.settings().rangeIncreasePer10s());
        return Math.abs(a.elo() - b.elo()) <= Math.max(50, range);
    }

    private void pushStatus() {
        if (tickCounter % 3 != 0) {
            return;
        }
        for (QueueEntry entry : queue.values()) {
            Player player = Bukkit.getPlayer(entry.uuid());
            if (player == null) {
                continue;
            }
            int waited = (int) ((System.currentTimeMillis() - entry.joinedAt()) / 1000L);
            int range = Math.min(plugin.settings().maxEloRange(),
                    plugin.settings().initialEloRange() + (waited / 10) * plugin.settings().rangeIncreasePer10s());
            player.sendActionBar(plugin.messages().get("queue.searching",
                    "mode", entry.mode().display(),
                    "time", String.valueOf(waited),
                    "size", String.valueOf(countOf(entry.mode(), entry.ranked())),
                    "range", String.valueOf(range)));
        }
    }

    public record QueueEntry(UUID uuid, String name, GameMode mode, boolean ranked, long joinedAt, int elo) {
    }
}
