package com.newduel1204.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * 竞技场建筑生成器：全部程序化生成，不依赖任何 schem 文件，也不需要 WorldEdit。
 * <p>生成的方块数量很小（每个竞技场约 1~3 千个），启动时一次性写完即可，
 * 之后战斗过程中不再有任何地形生成开销。</p>
 */
public final class ArenaBuilder {

    private static final BlockData FLOOR_A = Material.STONE_BRICKS.createBlockData();
    private static final BlockData FLOOR_B = Material.POLISHED_ANDESITE.createBlockData();
    private static final BlockData FRAME = Material.SMOOTH_QUARTZ.createBlockData();
    private static final BlockData WALL = Material.STONE_BRICKS.createBlockData();
    private static final BlockData WALL_TOP = Material.POLISHED_ANDESITE.createBlockData();
    private static final BlockData LIGHT = Material.SEA_LANTERN.createBlockData();
    private static final BlockData RING_FLOOR_A = Material.SMOOTH_STONE.createBlockData();
    private static final BlockData RING_FLOOR_B = Material.POLISHED_DIORITE.createBlockData();
    private static final BlockData CRYSTAL_A = Material.OBSIDIAN.createBlockData();
    private static final BlockData CRYSTAL_B = Material.POLISHED_BLACKSTONE.createBlockData();
    private static final BlockData CRYSTAL_WALL = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
    private static final BlockData PAD_A = Material.RED_CONCRETE.createBlockData();
    private static final BlockData PAD_B = Material.BLUE_CONCRETE.createBlockData();
    private static final BlockData AIR = Material.AIR.createBlockData();

    private ArenaBuilder() {
    }

    /** 完整重建一个竞技场（先清空再建造），幂等。 */
    public static void build(Arena arena) {
        World world = arena.world();
        Location center = arena.center();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int size = arena.kind().size();
        int half = size / 2;

        // 先清空竞技场范围（最多 8 格高），保证重建干净
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                for (int y = -1; y <= 7; y++) {
                    world.getBlockAt(cx + x, cy + y, cz + z).setBlockData(AIR, false);
                }
            }
        }

        switch (arena.kind()) {
            case FLAT -> buildFlat(world, cx, cy, cz, half);
            case RING -> buildRing(world, cx, cy, cz, half);
            case CRYSTAL -> buildCrystal(world, cx, cy, cz, half);
        }

        paintPad(world, arena.spawnA(), PAD_A);
        paintPad(world, arena.spawnB(), PAD_B);
        arena.built(true);
    }

    private static void buildFlat(World world, int cx, int cy, int cz, int half) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                BlockData data = edge ? FRAME : (((x + z) & 1) == 0 ? FLOOR_A : FLOOR_B);
                world.getBlockAt(cx + x, cy - 1, cz + z).setBlockData(data, false);
            }
        }
        // 4 格高的围墙，防止跑出擂台
        for (int y = 0; y < 4; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean border = Math.abs(x) == half || Math.abs(z) == half;
                    if (!border) {
                        continue;
                    }
                    BlockData data = (y == 3) ? WALL_TOP : WALL;
                    world.getBlockAt(cx + x, cy + y, cz + z).setBlockData(data, false);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                world.getBlockAt(cx + sx * half, cy + 4, cz + sz * half).setBlockData(LIGHT, false);
            }
        }
    }

    private static void buildRing(World world, int cx, int cy, int cz, int half) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                BlockData data;
                if (edge) {
                    data = FRAME;
                } else {
                    data = (((x + z) & 1) == 0) ? RING_FLOOR_A : RING_FLOOR_B;
                }
                world.getBlockAt(cx + x, cy - 1, cz + z).setBlockData(data, false);
                // 平台下方加一层，避免从侧面看到单层方块
                world.getBlockAt(cx + x, cy - 2, cz + z).setBlockData(WALL, false);
            }
        }
    }

    private static void buildCrystal(World world, int cx, int cy, int cz, int half) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                BlockData data = edge ? CRYSTAL_WALL : (((x + z) & 1) == 0 ? CRYSTAL_A : CRYSTAL_B);
                world.getBlockAt(cx + x, cy - 1, cz + z).setBlockData(data, false);
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    if (Math.abs(x) != half && Math.abs(z) != half) {
                        continue;
                    }
                    world.getBlockAt(cx + x, cy + y, cz + z).setBlockData(CRYSTAL_WALL, false);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 4; y < 7; y++) {
                    world.getBlockAt(cx + sx * half, cy + y, cz + sz * half).setBlockData(CRYSTAL_WALL, false);
                }
                world.getBlockAt(cx + sx * half, cy + 7, cz + sz * half).setBlockData(LIGHT, false);
            }
        }
    }

    /** 出生点画一块 3x3 的队伍色地砖。 */
    private static void paintPad(World world, Location spawn, BlockData data) {
        int bx = spawn.getBlockX();
        int by = spawn.getBlockY() - 1;
        int bz = spawn.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(bx + x, by, bz + z).setBlockData(data, false);
            }
        }
    }
}
