package com.newduel1204;

import com.newduel1204.arena.ArenaManager;
import com.newduel1204.command.AdminCommand;
import com.newduel1204.command.DuelCommand;
import com.newduel1204.command.QueueCommand;
import com.newduel1204.config.Messages;
import com.newduel1204.config.Settings;
import com.newduel1204.kit.GameMode;
import com.newduel1204.kit.Kits;
import com.newduel1204.listener.ArenaListener;
import com.newduel1204.listener.CombatListener;
import com.newduel1204.listener.MenuListener;
import com.newduel1204.listener.PlayerListener;
import com.newduel1204.match.MatchManager;
import com.newduel1204.match.QueueManager;
import com.newduel1204.match.RequestManager;
import com.newduel1204.player.ProfileManager;
import com.newduel1204.player.SnapshotService;
import com.newduel1204.ui.Menus;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NewDuel1204 主类。
 *
 * <p>一个高性能、低占用的 1v1 竞技场插件：
 * 启动时用虚空生成器创建竞技场世界并预生成全部场地，对局时直接异步加载区块传送，
 * 全程不生成地形、不占用 tick；玩家数据采用"先落盘再改动 + 崩溃自动还原"的三重保护。</p>
 */
public final class NewDuel1204 extends JavaPlugin {

    private static NewDuel1204 instance;

    private Settings settings;
    private Messages messages;
    private ArenaManager arenas;
    private SnapshotService snapshots;
    private ProfileManager profiles;
    private MatchManager matches;
    private QueueManager queues;
    private RequestManager requests;
    private Menus menus;

    private final Map<UUID, GameMode> preferredModes = new ConcurrentHashMap<>();
    private ArenaListener arenaListener;

    public static NewDuel1204 get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        saveDefaultConfig();
        this.settings = new Settings(getConfig());
        this.messages = new Messages(this);
        this.messages.load();
        Kits.configure(settings.attackSpeed());

        this.snapshots = new SnapshotService(this);
        this.profiles = new ProfileManager(this);
        this.arenas = new ArenaManager(this);
        this.matches = new MatchManager(this);
        this.queues = new QueueManager(this);
        this.requests = new RequestManager(this);
        this.menus = new Menus(this);

        this.snapshots.initialize();
        this.profiles.initialize();

        registerListeners();
        registerCommands();

        this.arenas.initialize();
        this.queues.start();
        this.requests.start();

        // 每 5 分钟清理一次临时缓存（火焰弹冷却等），保证长期运行内存稳定
        Bukkit.getScheduler().runTaskTimer(this, () -> arenaListener.pruneCooldowns(), 6000L, 6000L);

        getLogger().info("NewDuel1204 已启用，耗时 " + (System.currentTimeMillis() - start) + "ms"
                + "（" + GameMode.values().length + " 种模式）");
    }

    @Override
    public void onDisable() {
        try {
            if (matches != null) {
                matches.shutdown();
            }
            if (queues != null) {
                queues.stop();
            }
            if (requests != null) {
                requests.stop();
            }
            if (snapshots != null) {
                int recovered = snapshots.recoverAllOnline();
                if (recovered > 0) {
                    getLogger().info("关闭时额外还原了 " + recovered + " 名玩家的数据。");
                }
            }
            if (arenas != null) {
                arenas.shutdown();
            }
            if (profiles != null) {
                profiles.shutdown();
            }
        } catch (Exception e) {
            getLogger().severe("关闭流程出现异常: " + e.getMessage());
        }
        getLogger().info("NewDuel1204 已关闭。");
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        this.arenaListener = new ArenaListener(this);
        Bukkit.getPluginManager().registerEvents(arenaListener, this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
    }

    private void registerCommands() {
        PluginCommand duel = getCommand("duel");
        if (duel != null) {
            DuelCommand executor = new DuelCommand(this);
            duel.setExecutor(executor);
            duel.setTabCompleter(executor);
        }
        PluginCommand queue = getCommand("queue");
        if (queue != null) {
            QueueCommand executor = new QueueCommand(this);
            queue.setExecutor(executor);
            queue.setTabCompleter(executor);
        }
        PluginCommand nd = getCommand("nd");
        if (nd != null) {
            AdminCommand executor = new AdminCommand(this);
            nd.setExecutor(executor);
            nd.setTabCompleter(executor);
        }
    }

    /** 热重载：配置、语言、匹配间隔。竞技场数量与世界名需要重启生效。 */
    public void reloadAll() {
        reloadConfig();
        this.settings = new Settings(getConfig());
        this.messages.load();
        Kits.configure(settings.attackSpeed());
        this.queues.start();
        this.requests.stop();
        this.requests.start();
    }

    /* --------------------------------------------------------------- accessors */

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public SnapshotService snapshots() {
        return snapshots;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public MatchManager matches() {
        return matches;
    }

    public QueueManager queues() {
        return queues;
    }

    public RequestManager requests() {
        return requests;
    }

    public Menus menus() {
        return menus;
    }

    public GameMode preferredMode(UUID uuid) {
        return preferredModes.getOrDefault(uuid, GameMode.NODEBUFF);
    }

    public void preferredMode(UUID uuid, GameMode mode) {
        preferredModes.put(uuid, mode);
    }
}
