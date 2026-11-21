package com.newduel1204.arena;

/**
 * 竞技场形态。每种形态对应一套程序化生成的建筑，玩家进入时直接使用。
 */
public enum ArenaKind {

    /** 标准平坦擂台：25x25 地板 + 4 格外墙，绝大多数模式使用。 */
    FLAT("标准擂台", 25, 0),

    /** 高空相扑圆台：15x15 平台，四周是虚空，被击落即负。 */
    RING("相扑高台", 15, 20),

    /** 水晶战场：33x33 黑曜石地面，足够大，专门给水晶 PVP 用。 */
    CRYSTAL("水晶战场", 33, 0);

    private final String displayName;
    private final int size;
    private final int elevation;

    ArenaKind(String displayName, int size, int elevation) {
        this.displayName = displayName;
        this.size = size;
        this.elevation = elevation;
    }

    public String displayName() {
        return displayName;
    }

    /** 平台边长（奇数，保证有中心格）。 */
    public int size() {
        return size;
    }

    /** 相对 base-y 的抬高高度。 */
    public int elevation() {
        return elevation;
    }

    /** 两名玩家的出生点相对于中心的横轴偏移。 */
    public int spawnOffset() {
        return switch (this) {
            case FLAT -> 7;
            case RING -> 5;
            case CRYSTAL -> 11;
        };
    }
}
