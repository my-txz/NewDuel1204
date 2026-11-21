package com.newduel1204.arena;

import com.newduel1204.NewDuel1204;
import com.newduel1204.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 竞技场世界与竞技场池的管理者。
 * <p>核心思路：插件启用时创建一个纯虚空世界，并按网格把 N 个竞技场一次性"预生成"写入磁盘。
 * 需要开打时只需要异步加载对应区块（磁盘读取，毫秒级），玩家直接传送过去即可，
 * 战斗过程中完全不做地形生成 / 方块运算，因此 TPS 占用极低。</p>
 */
public final class ArenaManager {

    private final NewDuel1204 plugin;
    private final Map<Integer, Arena> arenas = new LinkedHashMap<>();
    private World world;
    private BukkitRunnable pregenTask;
    private int pregenDone;
    private int pregenTotal;
    private long pregenStartMillis;
    private boolean ready;

    public ArenaManager(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------- lifecycle */

    public void initialize() {
        Settings settings = plugin.settings();
        world = Bukkit.getWorld(settings.worldName());
        if (world == null) {
            world = createWorld(settings.worldName());
            if (world != null) {
                plugin.messages().send(Bukkit.getConsoleSender(), "admin.world-created",
                        "world", settings.worldName());
            }
        }
        if (world == null) {
            plugin.getLogger().severe("竞技场世界创建失败，决斗功能将不可用！");
            return;
        }
        applyWorldRules(world);
        allocateArenas(settings);

        if (settings.pregenerateOnStart()) {
            schedulePregeneration();
        } else {
            ready = true;
            plugin.getLogger().info("已跳过预生成，竞技场将在首次使用时建造。");
        }
    }

    private World createWorld(String name) {
        WorldCreator creator = new WorldCreator(name)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generateStructures(false);
        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);
        try {
            return creator.createWorld();
        } catch (Exception e) {
            plugin.getLogger().severe("创建世界 " + name + " 失败: " + e.getMessage());
            return null;
        }
    }

    private void applyWorldRules(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setAutoSave(true);
        world.setKeepSpawnInMemory(false);
        world.setSpawnLocation(0, plugin.settings().baseY() + 1, 0);
        setRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        setRule(world, GameRule.DO_WEATHER_CYCLE, false);
        setRule(world, GameRule.DO_MOB_SPAWNING, false);
        setRule(world, GameRule.DO_FIRE_TICK, false);
        setRule(world, GameRule.MOB_GRIEFING, false);
        setRule(world, GameRule.DO_IMMEDIATE_RESPAWN, true);
        setRule(world, GameRule.SPAWN_RADIUS, 0);
        setRule(world, GameRule.RANDOM_TICK_SPEED, 0);
        setRule(world, GameRule.ANNOUNCE_ADVANCEMENTS, false);
        setRule(world, GameRule.DISABLE_RAIDS, true);
        setRule(world, GameRule.DO_PATROL_SPAWNING, false);
        setRule(world, GameRule.DO_TRADER_SPAWNING, false);
        setRule(world, GameRule.DO_INSOMNIA, false);
        setRule(world, GameRule.DO_WARDEN_SPAWNING, false);
        // 双重保险：即使插件的数据还原出现极端异常，也不会因为死亡而丢物品
        setRule(world, GameRule.KEEP_INVENTORY, true);
    }

    private <T> void setRule(World world, GameRule<T> rule, T value) {
        try {
            world.setGameRule(rule, value);
        } catch (Exception ignored) {
            // 某些规则在旧版本不存在，忽略
        }
    }

    private void allocateArenas(Settings settings) {
        arenas.clear();
        int count = settings.arenaCount();
        int cols = (int) Math.ceil(Math.sqrt(count));
        int cell = settings.cellSize();

        for (int i = 0; i < count; i++) {
            ArenaKind kind = kindFor(i);
            int col = i % cols;
            int row = i / cols;
            int x = (col + 1) * cell;
            int z = (row + 1) * cell;
            int y = settings.baseY() + kind.elevation();

            Location center = new Location(world, x + 0.5, y, z + 0.5);
            int offset = kind.spawnOffset();
            Location spawnA = new Location(world, x - offset + 0.5, y, z + 0.5, -90f, 0f);
            Location spawnB = new Location(world, x + offset + 0.5, y, z + 0.5, 90f, 0f);
            int pad = kind.size() / 2 + 8;
            com.newduel1204.util.Cuboid bounds = new com.newduel1204.util.Cuboid(
                    world, x - pad, y - 10, z - pad, x + pad, y + 20, z + pad);

            arenas.put(i, new Arena(i, kind, center, spawnA, spawnB, bounds, settings.maxUndoBlocks()));
        }
        plugin.getLogger().info("已分配 " + arenas.size() + " 个竞技场（" + cols + "x" + cols + " 网格）");
    }

    /** 每 4 个竞技场循环一次形态：12 个标准擂台、6 个相扑高台、6 个水晶战场。 */
    private ArenaKind kindFor(int index) {
        return switch (index % 4) {
            case 1 -> ArenaKind.CRYSTAL;
            case 3 -> ArenaKind.RING;
            default -> ArenaKind.FLAT;
        };
    }

    /* ---------------------------------------------------------- pregeneration */

    private void schedulePregeneration() {
        pregenTotal = arenas.size();
        pregenDone = 0;
        pregenStartMillis = System.currentTimeMillis();
        final int perTick = plugin.settings().arenaPregeneratePerTick();

        pregenTask = new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                for (int n = 0; n < perTick && index < pregenTotal; n++) {
                    Arena arena = arenas.get(index++);
                    try {
                        ArenaBuilder.build(arena);
                        pregenDone++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("预生成竞技场 " + arena + " 失败: " + e.getMessage());
                    }
                }
                if (index >= pregenTotal) {
                    cancel();
                    finishPregeneration();
                }
            }
        };
        pregenTask.runTaskTimer(plugin, 20L, 1L);
        plugin.getLogger().info("开始后台预生成 " + pregenTotal + " 个竞技场…");
    }

    private void finishPregeneration() {
        ready = true;
        long ms = System.currentTimeMillis() - pregenStartMillis;
        plugin.messages().send(Bukkit.getConsoleSender(), "admin.pregenerated", "ms", String.valueOf(ms));
    }

    /** 检查场地中心地板是否还在（区块被卸载 / 世界被外部修改时用于自动修复）。 */
    private boolean platformIntact(Arena arena) {
        try {
            Location center = arena.center();
            return center.getWorld() != null
                    && center.getWorld().getBlockAt(center.getBlockX(), center.getBlockY() - 1,
                    center.getBlockZ()).getType() != org.bukkit.Material.AIR;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean ready() {
        return ready;
    }

    public int pregenDone() {
        return pregenDone;
    }

    public int pregenTotal() {
        return pregenTotal;
    }

    public World world() {
        return world;
    }

    public Collection<Arena> arenas() {
        return arenas.values();
    }

    public Arena arena(int id) {
        return arenas.get(id);
    }

    /** 根据坐标定位竞技场，用于爆炸保护与方块回滚。 */
    public Arena arenaAt(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return null;
        }
        for (Arena arena : arenas.values()) {
            if (arena.bounds().contains(location)) {
                return arena;
            }
        }
        return null;
    }

    public List<Arena> idleArenas() {
        List<Arena> list = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.state() == Arena.State.IDLE) {
                list.add(arena);
            }
        }
        return list;
    }

    /* --------------------------------------------------------------- acquire */

    /** 占用一个空闲竞技场（精确匹配形态，找不到时退化为任意空闲标准擂台）。 */
    public Optional<Arena> acquire(ArenaKind kind) {
        Arena fallback = null;
        for (Arena arena : arenas.values()) {
            if (arena.state() != Arena.State.IDLE) {
                continue;
            }
            if (arena.kind() == kind) {
                arena.state(Arena.State.RESERVED);
                return Optional.of(arena);
            }
            if (fallback == null && arena.kind() == ArenaKind.FLAT && kind != ArenaKind.RING) {
                fallback = arena;
            }
        }
        if (fallback != null) {
            fallback.state(Arena.State.RESERVED);
            return Optional.of(fallback);
        }
        return Optional.empty();
    }

    /** 异步准备竞技场：加区块票据 + 加载区块 + 必要时补建。 */
    public CompletableFuture<Boolean> prepare(Arena arena) {
        // 双重校验：内存标记 + 实际方块，防止区块被卸载后场地丢失
        if (!arena.built() || !platformIntact(arena)) {
            try {
                ArenaBuilder.build(arena);
            } catch (Exception e) {
                plugin.getLogger().warning("建造竞技场 " + arena + " 失败: " + e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }
        World world = arena.world();
        var bounds = arena.bounds();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                try {
                    world.addPluginChunkTicket(cx, cz, plugin);
                } catch (Exception ignored) {
                }
                futures.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> true)
                .exceptionally(t -> {
                    plugin.getLogger().warning("加载竞技场 " + arena + " 区块失败: " + t.getMessage());
                    return false;
                });
    }

    /** 释放竞技场：回滚方块、清理实体、移出区块票据。 */
    public void release(Arena arena) {
        if (arena == null) {
            return;
        }
        try {
            arena.rollback();
            arena.clearEntities();
        } catch (Exception e) {
            plugin.getLogger().warning("重置竞技场 " + arena + " 时出错: " + e.getMessage());
        }
        removeTickets(arena);
        arena.state(Arena.State.IDLE);
    }

    private void removeTickets(Arena arena) {
        World world = arena.world();
        var bounds = arena.bounds();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                try {
                    world.removePluginChunkTicket(cx, cz, plugin);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void rebuild(Arena arena) {
        release(arena);
        ArenaBuilder.build(arena);
    }

    public void shutdown() {
        if (pregenTask != null) {
            try {
                pregenTask.cancel();
            } catch (Exception ignored) {
            }
        }
        for (Arena arena : arenas.values()) {
            if (arena.state() != Arena.State.IDLE) {
                release(arena);
            }
        }
        if (world != null) {
            try {
                world.save();
            } catch (Exception ignored) {
            }
        }
    }
}
