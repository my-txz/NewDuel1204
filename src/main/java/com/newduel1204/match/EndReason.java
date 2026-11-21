package com.newduel1204.match;

/**
 * 对局结束原因。
 */
public enum EndReason {

    KILL("击杀"),
    RING_OUT("击落出界"),
    HITS("命中数领先"),
    TIMEOUT("时间到"),
    QUIT("对手退出"),
    FORFEIT("认输"),
    ADMIN("管理员中止"),
    DRAW("平局");

    private final String display;

    EndReason(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
