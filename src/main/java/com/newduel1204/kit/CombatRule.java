package com.newduel1204.kit;

/**
 * 战斗规则：决定伤害如何处理。
 */
public enum CombatRule {
    /** 正常伤害。 */
    NORMAL,
    /** 不造成实际伤害，只统计命中次数（相扑 / 拳击）。 */
    NO_DAMAGE,
    /** 一击必杀（伤害被放大到致命值）。 */
    ONE_HIT
}
