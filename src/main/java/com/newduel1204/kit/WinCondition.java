package com.newduel1204.kit;

/**
 * 胜负判定方式。
 */
public enum WinCondition {
    /** 击杀对手。 */
    KILL,
    /** 把对手打下高台（坠落 / 出界）。 */
    RING_OUT,
    /** 先累计到目标命中数（或时间到时命中更多）。 */
    HITS
}
