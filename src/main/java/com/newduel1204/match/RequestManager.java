package com.newduel1204.match;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邀请管理器：一个玩家可以同时收到多个邀请，60 秒自动过期。
 */
public final class RequestManager {

    private final NewDuel1204 plugin;
    /** target -> (sender -> request) */
    private final Map<UUID, Map<UUID, DuelRequest>> incoming = new ConcurrentHashMap<>();
    private BukkitTask task;

    public RequestManager(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        incoming.clear();
    }

    public DuelRequest send(Player sender, Player target, GameMode mode) {
        DuelRequest request = new DuelRequest(sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), mode, System.currentTimeMillis());
        incoming.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(sender.getUniqueId(), request);
        return request;
    }

    public Optional<DuelRequest> find(Player target, String senderName) {
        Map<UUID, DuelRequest> map = incoming.get(target.getUniqueId());
        if (map == null) {
            return Optional.empty();
        }
        for (DuelRequest request : map.values()) {
            if (request.senderName().equalsIgnoreCase(senderName)) {
                return request.expired() ? Optional.empty() : Optional.of(request);
            }
        }
        return Optional.empty();
    }

    public Optional<DuelRequest> latest(Player target) {
        Map<UUID, DuelRequest> map = incoming.get(target.getUniqueId());
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        DuelRequest best = null;
        for (DuelRequest request : map.values()) {
            if (request.expired()) {
                continue;
            }
            if (best == null || request.createdAt() > best.createdAt()) {
                best = request;
            }
        }
        return Optional.ofNullable(best);
    }

    public void remove(Player target, UUID sender) {
        Map<UUID, DuelRequest> map = incoming.get(target.getUniqueId());
        if (map != null) {
            map.remove(sender);
            if (map.isEmpty()) {
                incoming.remove(target.getUniqueId());
            }
        }
    }

    public void removeAll(Player target) {
        incoming.remove(target.getUniqueId());
    }

    public List<DuelRequest> requestsOf(Player target) {
        Map<UUID, DuelRequest> map = incoming.get(target.getUniqueId());
        if (map == null) {
            return List.of();
        }
        List<DuelRequest> list = new ArrayList<>(map.values());
        list.removeIf(DuelRequest::expired);
        return list;
    }

    public int size() {
        int count = 0;
        for (Map<UUID, DuelRequest> map : incoming.values()) {
            count += map.size();
        }
        return count;
    }

    private void expire() {
        for (Map.Entry<UUID, Map<UUID, DuelRequest>> entry : new ArrayList<>(incoming.entrySet())) {
            entry.getValue().entrySet().removeIf(e -> {
                boolean expired = e.getValue().expired();
                if (expired) {
                    Player target = Bukkit.getPlayer(entry.getKey());
                    if (target != null) {
                        plugin.messages().send(target, "duel.invite-expired", "player", e.getValue().senderName());
                    }
                }
                return expired;
            });
            if (entry.getValue().isEmpty()) {
                incoming.remove(entry.getKey());
            }
        }
    }
}
