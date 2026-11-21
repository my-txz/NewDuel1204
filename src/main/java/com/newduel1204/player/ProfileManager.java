package com.newduel1204.player;

import com.newduel1204.NewDuel1204;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家档案的内存缓存 + 异步持久化。
 *
 * <p>性能取舍：档案数量级远小于区块/实体，全部常驻内存（每个约 1KB），
 * 排行榜、匹配分差计算都在内存中完成，避免任何同步磁盘 IO。
 * 写入使用"脏标记 + 定时异步批量落盘"，关闭服务器时同步刷盘。</p>
 */
public final class ProfileManager {

    private final NewDuel1204 plugin;
    private final File dir;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private BukkitTask autosaveTask;
    private volatile boolean loading = true;

    public ProfileManager(NewDuel1204 plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
    }

    public void initialize() {
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe("无法创建玩家数据目录: " + dir.getAbsolutePath());
        }
        loading = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long start = System.currentTimeMillis();
            AtomicInteger count = new AtomicInteger();
            File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                        PlayerProfile profile = PlayerProfile.read(yaml, uuid, plugin.settings().startElo());
                        cache.putIfAbsent(uuid, profile);
                        count.incrementAndGet();
                    } catch (Exception e) {
                        plugin.getLogger().warning("读取玩家数据失败: " + file.getName() + " (" + e.getMessage() + ")");
                    }
                }
            }
            loading = false;
            plugin.getLogger().info("已载入 " + count.get() + " 份玩家档案，耗时 "
                    + (System.currentTimeMillis() - start) + "ms");
        });
        startAutosave();
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long ticks = plugin.settings().autosaveSeconds() * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> saveAll(true), ticks, ticks);
    }

    /* ------------------------------------------------------------------ access */

    public PlayerProfile get(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            return profile;
        }
        PlayerProfile loaded = load(uuid);
        if (loaded != null) {
            return loaded;
        }
        PlayerProfile created = new PlayerProfile(uuid, plugin.settings().startElo());
        PlayerProfile existing = cache.putIfAbsent(uuid, created);
        return existing == null ? created : existing;
    }

    public PlayerProfile get(Player player) {
        PlayerProfile profile = get(player.getUniqueId());
        profile.name = player.getName();
        return profile;
    }

    private PlayerProfile load(UUID uuid) {
        File file = fileOf(uuid);
        if (!file.exists()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            PlayerProfile profile = PlayerProfile.read(yaml, uuid, plugin.settings().startElo());
            PlayerProfile existing = cache.putIfAbsent(uuid, profile);
            return existing == null ? profile : existing;
        } catch (Exception e) {
            plugin.getLogger().warning("读取玩家数据失败 " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private File fileOf(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public int loadedCount() {
        return cache.size();
    }

    public boolean loading() {
        return loading;
    }

    /* -------------------------------------------------------------------- save */

    public void save(UUID uuid, boolean async) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) {
            return;
        }
        profile.lastSeen = System.currentTimeMillis();
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(profile));
        } else {
            write(profile);
        }
    }

    /** 批量落盘；异步模式下每份档案独立任务，避免一次性长时间占用。 */
    public void saveAll(boolean async) {
        if (dirty.isEmpty()) {
            return;
        }
        List<UUID> targets = new ArrayList<>(dirty);
        dirty.clear();
        for (UUID uuid : targets) {
            save(uuid, async);
        }
    }

    public void saveAllBlocking() {
        for (PlayerProfile profile : cache.values()) {
            write(profile);
        }
        dirty.clear();
    }

    private synchronized void write(PlayerProfile profile) {
        File file = fileOf(profile.uuid);
        try {
            YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            profile.write(yaml);
            File temp = new File(dir, profile.uuid + ".tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家数据失败 " + profile.uuid + ": " + e.getMessage());
            dirty.add(profile.uuid);
        }
    }

    /* ------------------------------------------------------------- leaderboard */

    /** mode 为 null 或 "global" 时按总 ELO 排序。 */
    public List<PlayerProfile.Standing> top(String mode, int limit) {
        List<PlayerProfile.Standing> list = new ArrayList<>();
        boolean global = mode == null || mode.isBlank() || mode.equalsIgnoreCase("global");
        for (PlayerProfile profile : cache.values()) {
            if (profile.wins + profile.losses <= 0) {
                continue;
            }
            int value = global ? profile.elo : profile.modeElo(mode, plugin.settings().startElo());
            list.add(new PlayerProfile.Standing(profile.uuid, profile.name, value,
                    global ? profile.wins : profile.modeWins(mode),
                    global ? profile.losses : profile.modeLosses(mode)));
        }
        list.sort(Comparator.comparingInt(PlayerProfile.Standing::elo).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public int rankOf(UUID uuid, String mode) {
        boolean global = mode == null || mode.isBlank() || mode.equalsIgnoreCase("global");
        int myValue = global ? get(uuid).elo : get(uuid).modeElo(mode, plugin.settings().startElo());
        int rank = 1;
        for (PlayerProfile profile : cache.values()) {
            if (profile.uuid.equals(uuid) || profile.wins + profile.losses <= 0) {
                continue;
            }
            int value = global ? profile.elo : profile.modeElo(mode, plugin.settings().startElo());
            if (value > myValue) {
                rank++;
            }
        }
        return rank;
    }

    public void reset(UUID uuid) {
        PlayerProfile profile = get(uuid);
        profile.elo = plugin.settings().startElo();
        profile.wins = 0;
        profile.losses = 0;
        profile.kills = 0;
        profile.deaths = 0;
        profile.streak = 0;
        profile.bestStreak = 0;
        profile.rankedMatches = 0;
        profile.modeElo.clear();
        profile.modeRecords.clear();
        profile.history.clear();
        dirty.add(uuid);
        save(uuid, false);
    }

    public void resetSeason() {
        int start = plugin.settings().startElo();
        for (PlayerProfile profile : cache.values()) {
            profile.elo = start;
            profile.modeElo.clear();
            profile.rankedMatches = 0;
            profile.streak = 0;
            dirty.add(profile.uuid);
        }
        saveAll(true);
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        saveAllBlocking();
    }
}
