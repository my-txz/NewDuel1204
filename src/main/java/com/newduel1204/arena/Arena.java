package com.newduel1204.arena;

import com.newduel1204.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个竞技场实例：位置、出生点、状态，以及可回滚的方块修改记录。
 */
public final class Arena {

    public enum State {
        IDLE("空闲", "<green>"),
        RESERVED("准备中", "<yellow>"),
        IN_USE("对战中", "<red>");

        private final String display;
        private final String color;

        State(String display, String color) {
            this.display = display;
            this.color = color;
        }

        public String display() {
            return display;
        }

        public String color() {
            return color;
        }
    }

    /** 方块坐标紧凑键，避免用 Location 当 Map key 造成大量对象。 */
    public record BlockPos(int x, int y, int z) {
    }

    private final int id;
    private final ArenaKind kind;
    private final Location center;
    private final Location spawnA;
    private final Location spawnB;
    private final Location spectatorSpawn;
    private final Cuboid bounds;
    private final int maxUndo;

    private final Map<BlockPos, BlockData> undo = new LinkedHashMap<>();
    private State state = State.IDLE;
    private boolean built;
    private boolean undoOverflow;
    private int undoOverflowCount;

    public Arena(int id, ArenaKind kind, Location center, Location spawnA, Location spawnB, Cuboid bounds, int maxUndo) {
        this.id = id;
        this.kind = kind;
        this.center = center;
        this.spawnA = spawnA;
        this.spawnB = spawnB;
        this.spectatorSpawn = center.clone().add(0, 13, 0);
        this.bounds = bounds;
        this.maxUndo = maxUndo;
    }

    public int id() {
        return id;
    }

    public ArenaKind kind() {
        return kind;
    }

    public Location center() {
        return center.clone();
    }

    public World world() {
        return center.getWorld();
    }

    public Location spawnA() {
        return spawnA.clone();
    }

    public Location spawnB() {
        return spawnB.clone();
    }

    public Location spectatorSpawn() {
        return spectatorSpawn.clone();
    }

    public Cuboid bounds() {
        return bounds;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    public boolean built() {
        return built;
    }

    public void built(boolean built) {
        this.built = built;
    }

    public boolean undoOverflow() {
        return undoOverflow;
    }

    /** 记录被修改方块的原状态，用于对局结束后回滚（水晶爆炸、搭桥等）。 */
    public void recordBlock(int x, int y, int z, BlockData previous) {
        BlockPos pos = new BlockPos(x, y, z);
        if (undo.containsKey(pos)) {
            return;
        }
        if (undo.size() >= maxUndo) {
            undoOverflow = true;
            undoOverflowCount++;
            return;
        }
        undo.put(pos, previous);
    }

    /** 回滚所有方块，返回恢复数量。 */
    public int rollback() {
        if (undo.isEmpty()) {
            return 0;
        }
        World world = world();
        int restored = 0;
        Iterator<Map.Entry<BlockPos, BlockData>> it = undo.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockData> entry = it.next();
            BlockPos pos = entry.getKey();
            try {
                world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(entry.getValue(), false);
                restored++;
            } catch (Exception ignored) {
                // 区块未加载等异常忽略，不阻塞流程
            }
            it.remove();
        }
        undoOverflow = false;
        undoOverflowCount = 0;
        return restored;
    }

    public int undoSize() {
        return undo.size();
    }

    /** 清理竞技场范围内的杂项实体，保持世界干净（低占用）。 */
    public int clearEntities() {
        World world = world();
        double r = bounds.radius();
        int removed = 0;
        for (Entity entity : world.getNearbyEntities(center, r, 26, r)) {
            if (entity instanceof Player) {
                continue;
            }
            if (entity instanceof Item || entity instanceof ExperienceOrb || entity instanceof Projectile
                    || entity instanceof EnderCrystal || entity instanceof TNTPrimed || entity instanceof FallingBlock
                    || entity instanceof ArmorStand || entity instanceof ItemFrame || entity instanceof Firework
                    || entity instanceof AreaEffectCloud || entity instanceof Boat || entity instanceof Minecart) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public String stateColor() {
        return state.color();
    }

    public String posString() {
        return center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ();
    }

    @Override
    public String toString() {
        return "#" + id + "(" + kind.name() + ")";
    }
}
