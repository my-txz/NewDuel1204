package com.newduel1204.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 配置读取层：全部通过方法读取，重载时直接新建一个实例即可，不需要到处刷新字段。
 */
public final class Settings {

    private final FileConfiguration c;

    public Settings(FileConfiguration config) {
        this.c = config;
    }

    /* ------------------------------------------------------------------ world */

    public String worldName() {
        return c.getString("world.name", "newduel_arena");
    }

    public int arenaCount() {
        return clamp(c.getInt("world.arena-count", 24), 1, 256);
    }

    public int baseY() {
        return clamp(c.getInt("world.base-y", 100), -50, 300);
    }

    public int cellSize() {
        return clamp(c.getInt("world.cell-size", 128), 96, 512);
    }

    public boolean pregenerateOnStart() {
        return c.getBoolean("world.pregenerate-on-start", true);
    }

    public int voidFallOffset() {
        return clamp(c.getInt("world.void-fall-offset", 30), 5, 200);
    }

    /* ------------------------------------------------------------------ match */

    public int countdownSeconds() {
        return clamp(c.getInt("match.countdown-seconds", 3), 0, 10);
    }

    public int endDelaySeconds() {
        return clamp(c.getInt("match.end-delay-seconds", 4), 1, 15);
    }

    public int defaultTimeLimit() {
        return clamp(c.getInt("match.default-time-limit", 600), 30, 7200);
    }

    public boolean allowSpectate() {
        return c.getBoolean("match.allow-spectate", true);
    }

    public boolean broadcastResults() {
        return c.getBoolean("match.broadcast-results", true);
    }

    public boolean blockCommands() {
        return c.getBoolean("match.block-commands", true);
    }

    public List<String> blockedCommands() {
        return normalizeCommands(c.getStringList("match.blocked-commands"));
    }

    public List<String> allowedCommands() {
        return normalizeCommands(c.getStringList("match.allow-commands"));
    }

    public boolean disableItemDrop() {
        return c.getBoolean("match.disable-item-drop", true);
    }

    public boolean disableItemPickup() {
        return c.getBoolean("match.disable-item-pickup", true);
    }

    /** 攻击速度：16.0 = 接近 1.8 的快速连击手感，4.0 = 原版 1.9 冷却。 */
    public double attackSpeed() {
        return Math.max(0.5, Math.min(64.0, c.getDouble("match.attack-speed", 16.0)));
    }

    /* ----------------------------------------------------------------- ranked */

    public boolean rankedEnabled() {
        return c.getBoolean("ranked.enabled", true);
    }

    public int startElo() {
        return clamp(c.getInt("ranked.start-elo", 1000), 0, 5000);
    }

    public int kFactor() {
        return clamp(c.getInt("ranked.k-factor", 32), 1, 64);
    }

    public int provisionalMatches() {
        return clamp(c.getInt("ranked.provisional-matches", 10), 0, 100);
    }

    public int season() {
        return Math.max(1, c.getInt("ranked.season", 1));
    }

    public int rankedMinLevel() {
        return Math.max(0, c.getInt("ranked.min-level", 0));
    }

    /* ------------------------------------------------------------------ queue */

    public int queueIntervalTicks() {
        return clamp(c.getInt("queue.interval-ticks", 20), 5, 200);
    }

    public int initialEloRange() {
        return clamp(c.getInt("queue.initial-elo-range", 100), 0, 2000);
    }

    public int rangeIncreasePer10s() {
        return clamp(c.getInt("queue.range-increase-per-10s", 100), 0, 1000);
    }

    public int maxEloRange() {
        return clamp(c.getInt("queue.max-elo-range", 800), 0, 5000);
    }

    public int maxWaitSeconds() {
        return clamp(c.getInt("queue.max-wait-seconds", 420), 10, 3600);
    }

    /* ---------------------------------------------------------------- storage */

    public int autosaveSeconds() {
        return clamp(c.getInt("storage.autosave-seconds", 120), 10, 3600);
    }

    public int snapshotBackupHours() {
        return clamp(c.getInt("storage.snapshot-backup-hours", 72), 0, 8760);
    }

    /* ------------------------------------------------------------ performance */

    public int arenaPregeneratePerTick() {
        return clamp(c.getInt("performance.arena-pregenerate-per-tick", 1), 1, 8);
    }

    public int hudUpdateTicks() {
        return clamp(c.getInt("performance.hud-update-ticks", 10), 2, 100);
    }

    public int maxUndoBlocks() {
        return clamp(c.getInt("performance.max-undo-blocks", 30000), 1000, 200000);
    }

    /* ------------------------------------------------------------------ utils */

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<String> normalizeCommands(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (!v.startsWith("/")) {
                v = "/" + v;
            }
            out.add(v);
        }
        return out;
    }
}
