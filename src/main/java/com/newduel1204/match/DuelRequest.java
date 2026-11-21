package com.newduel1204.match;

import com.newduel1204.kit.GameMode;

import java.util.UUID;

/**
 * 一条对战邀请。
 */
public record DuelRequest(UUID sender, String senderName, UUID target, GameMode mode, long createdAt) {

    public boolean expired() {
        return System.currentTimeMillis() - createdAt > 60_000L;
    }

    public long ageSeconds() {
        return (System.currentTimeMillis() - createdAt) / 1000L;
    }
}
