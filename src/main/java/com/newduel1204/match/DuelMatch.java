package com.newduel1204.match;

import com.newduel1204.NewDuel1204;
import com.newduel1204.arena.Arena;
import com.newduel1204.kit.GameMode;
import com.newduel1204.player.PlayerSnapshot;
import com.newduel1204.ui.Sidebar;
import com.newduel1204.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一局对战。状态机：WAITING → COUNTDOWN → FIGHTING → ENDING → DONE。
 * <p>整局只使用一个 10 tick 的定时器，两名玩家共用一个记分板，
 * 因此 100 局同时进行也几乎不产生额外开销。</p>
 */
public final class DuelMatch {

    public enum State {
        WAITING, COUNTDOWN, FIGHTING, ENDING, DONE
    }

    private final NewDuel1204 plugin;
    private final UUID matchId = UUID.randomUUID();
    private final GameMode mode;
    private final boolean ranked;
    private final Arena arena;
    private final UUID[] players = new UUID[2];
    private final PlayerSnapshot[] snapshots = new PlayerSnapshot[2];
    private final Map<UUID, Integer> hits = new ConcurrentHashMap<>();
    private final Map<UUID, Double> damageDealt = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    private State state = State.WAITING;
    private int countdownTicks;
    private int elapsedTicks;
    private int timeLimitSeconds;
    private int finishDelayTicks;
    private int lastCountdownSecond = -1;
    private UUID winner;
    private UUID loser;
    private EndReason endReason;
    private Sidebar sidebar;
    private BukkitTask task;
    private long startedAt;

    public DuelMatch(NewDuel1204 plugin, GameMode mode, boolean ranked, Arena arena,
                     UUID playerA, PlayerSnapshot snapA, UUID playerB, PlayerSnapshot snapB) {
        this.plugin = plugin;
        this.mode = mode;
        this.ranked = ranked;
        this.arena = arena;
        this.players[0] = playerA;
        this.players[1] = playerB;
        this.snapshots[0] = snapA;
        this.snapshots[1] = snapB;
    }

    /* ------------------------------------------------------------------ start */

    public void begin() {
        Player a = Bukkit.getPlayer(players[0]);
        Player b = Bukkit.getPlayer(players[1]);
        if (a == null || b == null) {
            // 玩家在准备阶段掉线：直接进入结束流程，快照会在他们上线时还原
            this.state = State.ENDING;
            this.endReason = EndReason.QUIT;
            this.finishDelayTicks = 20;
            startTask();
            return;
        }

        this.sidebar = new Sidebar(Text.mm("<gradient:#5ee7ff:#a06bff><bold>DUEL</bold></gradient>"), 9);
        this.timeLimitSeconds = mode.timeLimit() > 0 ? mode.timeLimit() : plugin.settings().defaultTimeLimit();

        preparePlayer(a, arena.spawnA());
        preparePlayer(b, arena.spawnB());

        sidebar.show(a);
        sidebar.show(b);
        updateHud();

        this.state = State.COUNTDOWN;
        int seconds = plugin.settings().countdownSeconds();
        this.countdownTicks = Math.max(1, seconds * 20);
        if (seconds <= 0) {
            startFight();
        }
        startTask();
    }

    private void preparePlayer(Player player, Location spawn) {
        player.setInvulnerable(true);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setVelocity(player.getVelocity().zero());
        player.teleport(spawn);
        mode.kit().apply(player);
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : Math.min(maxHealth.getValue(), 20.0));
        player.setRemainingAir(player.getMaximumAir());
        hits.put(player.getUniqueId(), 0);
        damageDealt.put(player.getUniqueId(), 0.0);
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 10L);
    }

    /* ------------------------------------------------------------------- tick */

    private void tick() {
        switch (state) {
            case COUNTDOWN -> {
                countdownTicks -= 10;
                if (countdownTicks <= 0) {
                    startFight();
                } else {
                    int second = (int) Math.ceil(countdownTicks / 20.0);
                    if (second != lastCountdownSecond) {
                        lastCountdownSecond = second;
                        showCountdown(second);
                        updateHud();
                    }
                }
            }
            case FIGHTING -> {
                elapsedTicks += 10;
                if (elapsedTicks % 20 == 0) {
                    updateHud();
                    checkTimeout();
                }
                checkOutOfBounds();
                checkOnline();
            }
            case ENDING -> {
                finishDelayTicks -= 10;
                if (finishDelayTicks <= 0) {
                    finish();
                }
            }
            default -> {
            }
        }
    }

    private void startFight() {
        state = State.FIGHTING;
        startedAt = System.currentTimeMillis();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.setInvulnerable(false);
            title(player, "<gradient:#ff2d55:#ffd60a><bold>FIGHT!</bold></gradient>", "<gray>祝你好运", 8);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        }
        updateHud();
    }

    private void showCountdown(int second) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            title(player, "<yellow><bold>" + (second > 0 ? second : "GO") + "</bold></yellow>", "", 4);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f,
                    second <= 0 ? 2f : 1f + (3 - Math.min(3, second)) * 0.15f);
        }
    }

    private void checkTimeout() {
        if (elapsedTicks / 20 < timeLimitSeconds) {
            return;
        }
        if (mode.win() == com.newduel1204.kit.WinCondition.HITS) {
            int hitsA = hits.getOrDefault(players[0], 0);
            int hitsB = hits.getOrDefault(players[1], 0);
            if (hitsA == hitsB) {
                end(null, EndReason.DRAW);
            } else {
                end(hitsA > hitsB ? players[0] : players[1], EndReason.HITS);
            }
            return;
        }
        double healthA = healthOf(players[0]);
        double healthB = healthOf(players[1]);
        if (Math.abs(healthA - healthB) < 0.01) {
            end(null, EndReason.DRAW);
        } else {
            end(healthA > healthB ? players[0] : players[1], EndReason.TIMEOUT);
        }
    }

    private double healthOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? 0 : player.getHealth();
    }

    private void checkOutOfBounds() {
        double floorY = arena.center().getY() - 8;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (player.getLocation().getY() < floorY) {
                end(other(uuid), mode.win() == com.newduel1204.kit.WinCondition.RING_OUT
                        ? EndReason.RING_OUT : EndReason.KILL);
                return;
            }
        }
    }

    private void checkOnline() {
        for (UUID uuid : players) {
            if (Bukkit.getPlayer(uuid) == null) {
                end(other(uuid), EndReason.QUIT);
                return;
            }
        }
    }

    /* ------------------------------------------------------------------- HUD */

    public void updateHud() {
        if (sidebar == null) {
            return;
        }
        Player a = Bukkit.getPlayer(players[0]);
        Player b = Bukkit.getPlayer(players[1]);
        String nameA = a == null ? "离线" : a.getName();
        String nameB = b == null ? "离线" : b.getName();

        sidebar.line(0, "<dark_gray>模式 <white>" + mode.display());
        sidebar.line(1, "<dark_gray>类型 " + (ranked ? "<light_purple>排位" : "<green>匹配"));
        sidebar.line(2, " ");
        sidebar.line(3, "<red>● " + nameA + " <white>" + hearts(a));
        sidebar.line(4, "<blue>● " + nameB + " <white>" + hearts(b));
        sidebar.line(5, "  ");
        if (mode.win() == com.newduel1204.kit.WinCondition.HITS) {
            sidebar.line(6, "<dark_gray>命中 <white>" + hits.getOrDefault(players[0], 0) + " <dark_gray>/目标 "
                    + mode.hitTarget());
            sidebar.line(7, "<dark_gray>对手命中 <white>" + hits.getOrDefault(players[1], 0));
        } else {
            sidebar.line(6, "<dark_gray>剩余时间 <white>" + remainingTime());
            sidebar.line(7, "<dark_gray>已造成伤害 <white>" + String.format("%.1f",
                    damageDealt.getOrDefault(players[0], 0.0)));
        }
        sidebar.line(8, "<dark_gray>场地 <white>#" + arena.id());
    }

    private String hearts(Player player) {
        if (player == null) {
            return "-";
        }
        double health = Math.max(0, player.getHealth());
        int full = (int) Math.ceil(health / 2.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < full ? "❤" : "<dark_gray>❤");
        }
        return sb.toString();
    }

    public String remainingTime() {
        int left = Math.max(0, timeLimitSeconds - elapsedTicks / 20);
        return Text.formatTime(left);
    }

    /* ------------------------------------------------------------------ events */

    public void onDamage(UUID attacker, UUID victim, double damage) {
        if (state != State.FIGHTING) {
            return;
        }
        hits.merge(attacker, 1, Integer::sum);
        damageDealt.merge(attacker, damage, Double::sum);
        if (mode.win() == com.newduel1204.kit.WinCondition.HITS && mode.hitTarget() > 0) {
            if (hits.getOrDefault(attacker, 0) >= mode.hitTarget()) {
                end(attacker, EndReason.HITS);
            }
        }
        updateHud();
    }

    public void onDeath(Player victim) {
        if (state != State.FIGHTING) {
            return;
        }
        if (!isParticipant(victim.getUniqueId())) {
            return;
        }
        end(other(victim.getUniqueId()), EndReason.KILL);
    }

    public void onQuit(Player player) {
        if (!isParticipant(player.getUniqueId())) {
            return;
        }
        if (state == State.FIGHTING || state == State.COUNTDOWN) {
            end(other(player.getUniqueId()), EndReason.QUIT);
        }
    }

    /** 主动认输 / 离开。 */
    public void forfeit(Player player) {
        if (!isParticipant(player.getUniqueId())) {
            return;
        }
        if (state == State.FIGHTING || state == State.COUNTDOWN) {
            end(other(player.getUniqueId()), EndReason.FORFEIT);
        }
    }

    public void adminStop() {
        if (state == State.DONE) {
            return;
        }
        end(null, EndReason.ADMIN);
    }

    /* -------------------------------------------------------------------- end */

    public void end(UUID winnerId, EndReason reason) {
        if (state == State.ENDING || state == State.DONE) {
            return;
        }
        this.state = State.ENDING;
        this.winner = winnerId;
        this.loser = winnerId == null ? null : other(winnerId);
        this.endReason = reason;
        this.finishDelayTicks = Math.max(20, plugin.settings().endDelaySeconds() * 20);
        plugin.matches().handleEnd(this);
    }

    private void finish() {
        state = State.DONE;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (int i = 0; i < 2; i++) {
            UUID uuid = players[i];
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                // 玩家离线：快照文件保留，下次上线自动还原
                continue;
            }
            player.setInvulnerable(false);
            player.setFireTicks(0);
            boolean ok = plugin.snapshots().restore(player, snapshots[i]);
            try {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            } catch (Exception ignored) {
            }
            if (ok) {
                plugin.messages().send(player, "duel.restored");
            }
            plugin.matches().clearSession(uuid);
        }
        if (sidebar != null) {
            sidebar.destroy();
            sidebar = null;
        }
        plugin.matches().notifySpectators(this);
        plugin.arenas().release(arena);
        plugin.matches().remove(this);
    }

    /* ------------------------------------------------------------------ misc */

    public void addSpectator(Player player) {
        spectators.add(player.getUniqueId());
        player.teleport(arena.spectatorSpawn());
        if (sidebar != null) {
            sidebar.show(player);
        }
    }

    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    public java.util.Set<UUID> spectators() {
        return spectators;
    }

    public void broadcastToMatch(Component message) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public void title(Player player, String main, String sub, int fadeInTicks) {
        player.showTitle(Title.title(Text.mm(main), Text.mm(sub),
                Title.Times.times(Duration.ofMillis(fadeInTicks * 50L), Duration.ofMillis(900),
                        Duration.ofMillis(300))));
    }

    public void effectsEnd() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    /* -------------------------------------------------------------- accessors */

    public UUID matchId() {
        return matchId;
    }

    public GameMode mode() {
        return mode;
    }

    public boolean ranked() {
        return ranked;
    }

    public Arena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public boolean isParticipant(UUID uuid) {
        return players[0].equals(uuid) || players[1].equals(uuid);
    }

    public UUID other(UUID uuid) {
        return players[0].equals(uuid) ? players[1] : players[0];
    }

    public UUID[] players() {
        return players;
    }

    public PlayerSnapshot snapshot(UUID uuid) {
        return snapshots[0].uuid.equals(uuid) ? snapshots[0] : snapshots[1];
    }

    public UUID winner() {
        return winner;
    }

    public UUID loser() {
        return loser;
    }

    public EndReason endReason() {
        return endReason;
    }

    public Map<UUID, Integer> hits() {
        return hits;
    }

    public Map<UUID, Double> damageDealt() {
        return damageDealt;
    }

    public long startedAt() {
        return startedAt;
    }

    public int durationSeconds() {
        return elapsedTicks / 20;
    }

    public Map<String, Object> debugInfo() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", matchId.toString());
        map.put("mode", mode.id());
        map.put("ranked", ranked);
        map.put("arena", arena.id());
        map.put("state", state.name());
        map.put("players", players[0] + "," + players[1]);
        return map;
    }
}
