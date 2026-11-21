package com.newduel1204.util;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 轴对齐长方体区域。用于判定玩家是否仍在竞技场内。
 */
public final class Cuboid {

    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public World world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** 水平半径，用于 getNearbyEntities。 */
    public double radius() {
        return Math.max(maxX - minX, maxZ - minZ) / 2.0 + 4.0;
    }

    public Location center() {
        return new Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0 + 0.5);
    }
}
