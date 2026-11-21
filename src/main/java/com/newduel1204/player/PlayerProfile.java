package com.newduel1204.player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家长期数据（战绩 / 段位 / 生涯统计）。
 *
 * <p>向后兼容策略：每次写入前先读出旧文件，只覆盖自己认识的键，
 * 因此旧版本遗留的、或未来版本新增的字段都不会被抹掉；
 * 同时通过 {@code schema} 版本号执行增量迁移。</p>
 */
public final class PlayerProfile {

    /** 当前数据结构版本。低版本读入时会自动迁移。 */
    public static final int SCHEMA = 3;

    public UUID uuid;
    public String name = "";
    public int elo;
    public int season = 1;
    public int wins;
    public int losses;
    public int kills;
    public int deaths;
    public int streak;
    public int bestStreak;
    public int rankedMatches;
    public long lastSeen;
    public long playtimeSeconds;

    public final Map<String, Integer> modeElo = new LinkedHashMap<>();
    public final Map<String, int[]> modeRecords = new LinkedHashMap<>(); // [wins, losses]
    public final List<String> history = new ArrayList<>();

    public PlayerProfile(UUID uuid, int startElo) {
        this.uuid = uuid;
        this.elo = startElo;
    }

    public int modeElo(String modeId, int fallback) {
        return modeElo.getOrDefault(modeId, fallback);
    }

    public void setModeElo(String modeId, int value) {
        modeElo.put(modeId, value);
    }

    public int modeWins(String modeId) {
        int[] rec = modeRecords.get(modeId);
        return rec == null ? 0 : rec[0];
    }

    public int modeLosses(String modeId) {
        int[] rec = modeRecords.get(modeId);
        return rec == null ? 0 : rec[1];
    }

    public void addRecord(String modeId, boolean win) {
        int[] rec = modeRecords.computeIfAbsent(modeId, k -> new int[2]);
        if (win) {
            rec[0]++;
        } else {
            rec[1]++;
        }
    }

    public void addHistory(String line) {
        history.add(0, line);
        while (history.size() > 10) {
            history.remove(history.size() - 1);
        }
    }

    public double winRate() {
        int total = wins + losses;
        return total == 0 ? 0 : (wins * 100.0 / total);
    }

    /* ----------------------------------------------------------- persistence */

    public void write(YamlConfiguration yaml) {
        int previousSchema = yaml.getInt("schema", 1);
        if (previousSchema < SCHEMA) {
            migrate(yaml, previousSchema);
        }
        yaml.set("schema", SCHEMA);
        yaml.set("uuid", uuid.toString());
        yaml.set("name", name);
        yaml.set("elo", elo);
        yaml.set("season", season);
        yaml.set("stats.wins", wins);
        yaml.set("stats.losses", losses);
        yaml.set("stats.kills", kills);
        yaml.set("stats.deaths", deaths);
        yaml.set("stats.streak", streak);
        yaml.set("stats.best-streak", bestStreak);
        yaml.set("stats.ranked-matches", rankedMatches);
        yaml.set("stats.playtime", playtimeSeconds);
        yaml.set("last-seen", lastSeen);

        for (Map.Entry<String, Integer> entry : modeElo.entrySet()) {
            yaml.set("modes." + entry.getKey() + ".elo", entry.getValue());
        }
        for (Map.Entry<String, int[]> entry : modeRecords.entrySet()) {
            String base = "modes." + entry.getKey();
            yaml.set(base + ".wins", entry.getValue()[0]);
            yaml.set(base + ".losses", entry.getValue()[1]);
        }
        yaml.set("history", history);
    }

    public static PlayerProfile read(YamlConfiguration yaml, UUID uuid, int startElo) {
        PlayerProfile profile = new PlayerProfile(uuid, startElo);
        int schema = yaml.getInt("schema", 1);
        if (schema < SCHEMA) {
            migrate(yaml, schema);
        }
        profile.name = yaml.getString("name", "");
        profile.elo = yaml.getInt("elo", startElo);
        profile.season = yaml.getInt("season", 1);
        profile.wins = yaml.getInt("stats.wins", yaml.getInt("wins", 0));
        profile.losses = yaml.getInt("stats.losses", yaml.getInt("losses", 0));
        profile.kills = yaml.getInt("stats.kills", yaml.getInt("kills", 0));
        profile.deaths = yaml.getInt("stats.deaths", yaml.getInt("deaths", 0));
        profile.streak = yaml.getInt("stats.streak", 0);
        profile.bestStreak = yaml.getInt("stats.best-streak", 0);
        profile.rankedMatches = yaml.getInt("stats.ranked-matches", 0);
        profile.playtimeSeconds = yaml.getLong("stats.playtime", 0L);
        profile.lastSeen = yaml.getLong("last-seen", 0L);

        ConfigurationSection modes = yaml.getConfigurationSection("modes");
        if (modes != null) {
            for (String key : modes.getKeys(false)) {
                ConfigurationSection mode = modes.getConfigurationSection(key);
                if (mode == null) {
                    continue;
                }
                if (mode.contains("elo")) {
                    profile.modeElo.put(key, mode.getInt("elo"));
                }
                int modeWins = mode.getInt("wins", 0);
                int modeLosses = mode.getInt("losses", 0);
                if (modeWins > 0 || modeLosses > 0) {
                    profile.modeRecords.put(key, new int[]{modeWins, modeLosses});
                }
            }
        }
        // v1 只有一个全局 elo，历史模式数据用全局值兜底
        if (profile.modeElo.isEmpty() && profile.elo > 0) {
            profile.modeElo.put("nodebuff", profile.elo);
        }
        profile.history.addAll(yaml.getStringList("history"));
        return profile;
    }

    /**
     * 结构迁移。老版本（schema 1/2）的字段位置与新版本不同，这里做平滑搬迁，
     * 保证服主升级插件后玩家数据不会清零。
     */
    private static void migrate(YamlConfiguration yaml, int from) {
        if (from <= 1) {
            // v1: 只有 wins / losses / kills / deaths 平铺字段
            if (!yaml.contains("stats.wins") && yaml.contains("wins")) {
                yaml.set("stats.wins", yaml.getInt("wins"));
                yaml.set("stats.losses", yaml.getInt("losses"));
                yaml.set("stats.kills", yaml.getInt("kills"));
                yaml.set("stats.deaths", yaml.getInt("deaths"));
            }
            yaml.set("schema", 2);
        }
        if (from <= 2) {
            // v2 -> v3: 增加赛季与历史记录字段
            if (!yaml.contains("season")) {
                yaml.set("season", 1);
            }
            if (!yaml.contains("history")) {
                yaml.set("history", new ArrayList<String>());
            }
            yaml.set("schema", 3);
        }
    }

    /** 供排行榜使用的快照（避免排序时持有可变对象）。 */
    public record Standing(UUID uuid, String name, int elo, int wins, int losses) {
        public static Standing of(PlayerProfile profile) {
            return new Standing(profile.uuid, profile.name, profile.elo, profile.wins, profile.losses);
        }
    }
}
