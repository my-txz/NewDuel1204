package com.newduel1204.player;

import com.newduel1204.NewDuel1204;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快照持久化服务 —— 保证"进竞技场前一定先落盘"。
 *
 * <p>数据安全策略（三道保险）：
 * <ol>
 *   <li><b>先存后改</b>：保存快照到磁盘成功之后，才允许替换玩家的背包/位置。
 *       一旦落盘失败，对局直接中止，玩家状态保持原样。</li>
 *   <li><b>崩溃恢复</b>：快照文件在还原成功之前不会删除。服务器崩溃、玩家掉线、
 *       插件被强制卸载，都会在下次启动/玩家上线时自动还原。</li>
 *   <li><b>世界级兜底</b>：竞技场世界开启 keepInventory，即使极端情况下死亡也不会掉物品。</li>
 * </ol>
 */
public final class SnapshotService {

    private final NewDuel1204 plugin;
    private final File snapshotDir;
    private final File backupDir;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public SnapshotService(NewDuel1204 plugin) {
        this.plugin = plugin;
        this.snapshotDir = new File(plugin.getDataFolder(), "snapshots");
        this.backupDir = new File(plugin.getDataFolder(), "snapshots-backup");
    }

    public void initialize() {
        if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
            plugin.getLogger().severe("无法创建快照目录: " + snapshotDir.getAbsolutePath());
        }
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        pending.clear();
        File[] files = snapshotDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                try {
                    pending.add(UUID.fromString(name));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (!pending.isEmpty()) {
            plugin.getLogger().warning("检测到 " + pending.size() + " 份未还原的玩家快照，将在玩家上线时自动还原。");
        }
    }

    /* ----------------------------------------------------------------- status */

    public boolean hasPending(UUID uuid) {
        return pending.contains(uuid);
    }

    public int pendingCount() {
        return pending.size();
    }

    public List<UUID> pendingIds() {
        return new ArrayList<>(pending);
    }

    public File fileOf(UUID uuid) {
        return new File(snapshotDir, uuid + ".yml");
    }

    /* ------------------------------------------------------------------- save */

    /**
     * 保存玩家状态到磁盘。
     *
     * @throws SnapshotException 保存失败（此时不允许继续后续的传送/换装操作）
     */
    public PlayerSnapshot save(Player player) throws SnapshotException {
        UUID uuid = player.getUniqueId();

        // 如果存在上一次未还原的快照，先把它还原，避免用竞技场装备覆盖玩家真实数据
        if (hasPending(uuid)) {
            plugin.getLogger().warning("玩家 " + player.getName() + " 存在未还原快照，先执行还原再保存新快照。");
            recover(player);
        }

        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshot.state = "PENDING";

        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.writeTo(yaml);
        File target = fileOf(uuid);
        File temp = new File(snapshotDir, uuid + ".tmp");
        try {
            yaml.save(temp);
            if (target.exists() && !target.delete()) {
                plugin.getLogger().warning("无法删除旧快照文件: " + target.getName());
            }
            Path moved = Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (!Files.exists(moved) || Files.size(moved) <= 0) {
                throw new IOException("快照文件写入异常");
            }
        } catch (Exception e) {
            throw new SnapshotException("无法写入快照文件: " + e.getMessage(), e);
        }

        pending.add(uuid);
        return snapshot;
    }

    /* ---------------------------------------------------------------- restore */

    /**
     * 还原玩家。
     *
     * @return true 表示完全成功
     */
    public boolean restore(Player player, PlayerSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        boolean problem;
        try {
            problem = snapshot.apply(player);
        } catch (Exception e) {
            plugin.getLogger().severe("还原玩家 " + player.getName() + " 数据时抛出异常: " + e.getMessage());
            problem = true;
        }

        if (problem) {
            plugin.getLogger().severe("玩家 " + player.getName() + " 的数据还原不完整，快照已保留在 "
                    + fileOf(player.getUniqueId()).getName() + "，可稍后重试。");
            plugin.messages().send(player, "duel.restore-failed");
            return false;
        }

        backup(player.getUniqueId());
        File file = fileOf(player.getUniqueId());
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("无法删除已还原的快照文件: " + file.getName());
        }
        pending.remove(player.getUniqueId());
        return true;
    }

    /** 尝试用磁盘上的快照还原玩家（用于上线恢复 / 崩溃恢复）。 */
    public boolean recover(Player player) {
        UUID uuid = player.getUniqueId();
        File file = fileOf(uuid);
        if (!file.exists()) {
            pending.remove(uuid);
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        PlayerSnapshot snapshot = PlayerSnapshot.readFrom(yaml);
        if (snapshot == null) {
            plugin.getLogger().severe("快照文件损坏: " + file.getName());
            return false;
        }
        if (!uuid.equals(snapshot.uuid)) {
            plugin.getLogger().severe("快照文件与玩家不匹配: " + file.getName());
            return false;
        }
        return restore(player, snapshot);
    }

    /** 服务器启动 / 关闭时的兜底：把所有在线玩家的待还原快照还原掉。 */
    public int recoverAllOnline() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasPending(player.getUniqueId()) && recover(player)) {
                count++;
            }
        }
        return count;
    }

    /* ---------------------------------------------------------------- backups */

    private void backup(UUID uuid) {
        File source = fileOf(uuid);
        if (!source.exists()) {
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(backupDir, uuid + "-" + stamp + ".yml");
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pruneBackups();
        } catch (IOException e) {
            plugin.getLogger().warning("快照备份失败: " + e.getMessage());
        }
    }

    private void pruneBackups() {
        int hours = plugin.settings().snapshotBackupHours();
        if (hours <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - hours * 3600_000L;
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.lastModified() < cutoff && !file.delete()) {
                plugin.getLogger().fine("无法删除过期备份: " + file.getName());
            }
        }
    }

    /** 统计信息，用于 /nd snapshot 命令。 */
    public Map<String, Long> stats() {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".yml"));
        map.put("pending", (long) pending.size());
        map.put("backups", backups == null ? 0L : (long) backups.length);
        return map;
    }

    public static final class SnapshotException extends Exception {
        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
