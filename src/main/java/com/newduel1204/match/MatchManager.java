package com.newduel1204.match;

import com.newduel1204.NewDuel1204;
import com.newduel1204.arena.Arena;
import com.newduel1204.kit.GameMode;
import com.newduel1204.player.PlayerProfile;
import com.newduel1204.player.PlayerSnapshot;
import com.newduel1204.player.Ranking;
import com.newduel1204.player.SnapshotService;
import com.newduel1204.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对局编排：开始、结束结算、观战、状态查询。
 */
public final class MatchManager {

    private final NewDuel1204 plugin;
    private final Map<UUID, DuelMatch> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, DuelMatch> matches = new ConcurrentHashMap<>();
    private final Map<UUID, SpectatorState> spectators = new ConcurrentHashMap<>();

    public MatchManager(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------ start */

    public void requestStart(Player a, Player b, GameMode mode, boolean ranked) {
        if (a == null || b == null || a.equals(b)) {
            return;
        }
        if (isBusy(a)) {
            plugin.messages().send(a, "duel.busy");
            return;
        }
        if (isBusy(b)) {
            plugin.messages().send(a, "duel.target-busy", "player", b.getName());
            return;
        }

        Optional<Arena> acquired = plugin.arenas().acquire(mode.arena());
        if (acquired.isEmpty()) {
            plugin.messages().send(a, "duel.no-arena");
            plugin.messages().send(b, "duel.no-arena");
            return;
        }
        Arena arena = acquired.get();
        plugin.arenas().prepare(arena).whenComplete((ok, throwable) -> {
            if (throwable != null || !Boolean.TRUE.equals(ok)) {
                plugin.arenas().release(arena);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(a, "duel.no-arena");
                    plugin.messages().send(b, "duel.no-arena");
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> actuallyStart(a, b, mode, ranked, arena));
        });
    }

    private void actuallyStart(Player a, Player b, GameMode mode, boolean ranked, Arena arena) {
        if (a == null || !a.isOnline() || b == null || !b.isOnline()) {
            plugin.arenas().release(arena);
            return;
        }
        if (isBusy(a) || isBusy(b)) {
            plugin.arenas().release(arena);
            plugin.messages().send(a, "duel.busy");
            return;
        }

        PlayerSnapshot snapA;
        try {
            snapA = plugin.snapshots().save(a);
        } catch (SnapshotService.SnapshotException e) {
            plugin.getLogger().severe("保存 " + a.getName() + " 快照失败，已取消对局: " + e.getMessage());
            plugin.messages().send(a, "duel.restore-failed");
            plugin.arenas().release(arena);
            return;
        }

        PlayerSnapshot snapB;
        try {
            snapB = plugin.snapshots().save(b);
        } catch (SnapshotService.SnapshotException e) {
            plugin.getLogger().severe("保存 " + b.getName() + " 快照失败，已取消对局: " + e.getMessage());
            // 还原已经保存的 A，避免状态残留
            plugin.snapshots().restore(a, snapA);
            plugin.messages().send(b, "duel.restore-failed");
            plugin.arenas().release(arena);
            return;
        }

        arena.state(Arena.State.IN_USE);
        DuelMatch match = new DuelMatch(plugin, mode, ranked, arena, a.getUniqueId(), snapA, b.getUniqueId(), snapB);
        register(match);
        match.begin();

        plugin.messages().send(a, "duel.starting");
        plugin.messages().send(b, "duel.starting");
    }

    private void register(DuelMatch match) {
        matches.put(match.matchId(), match);
        for (UUID uuid : match.players()) {
            byPlayer.put(uuid, match);
            plugin.queues().leave(uuid, false);
        }
    }

    /* -------------------------------------------------------------------- end */

    public void handleEnd(DuelMatch match) {
        Player winner = match.winner() == null ? null : Bukkit.getPlayer(match.winner());
        Player loser = match.loser() == null ? null : Bukkit.getPlayer(match.loser());
        GameMode mode = match.mode();
        boolean draw = match.winner() == null;
        String reason = match.endReason() == null ? "" : match.endReason().display();

        int deltaWinner = 0;
        int deltaLoser = 0;

        if (!draw) {
            UUID winnerId = match.winner();
            UUID loserId = match.loser();
            PlayerProfile winnerProfile = plugin.profiles().get(winnerId);
            PlayerProfile loserProfile = plugin.profiles().get(loserId);
            if (winner != null) {
                winnerProfile.name = winner.getName();
            }
            if (loser != null) {
                loserProfile.name = loser.getName();
            }

            winnerProfile.wins++;
            winnerProfile.kills++;
            winnerProfile.streak++;
            winnerProfile.bestStreak = Math.max(winnerProfile.bestStreak, winnerProfile.streak);
            winnerProfile.addRecord(mode.id(), true);

            loserProfile.losses++;
            loserProfile.deaths++;
            loserProfile.streak = 0;
            loserProfile.addRecord(mode.id(), false);

            if (match.ranked()) {
                int startElo = plugin.settings().startElo();
                int winnerElo = winnerProfile.modeElo(mode.id(), startElo);
                int loserElo = loserProfile.modeElo(mode.id(), startElo);
                boolean winnerProvisional = winnerProfile.rankedMatches < plugin.settings().provisionalMatches();
                boolean loserProvisional = loserProfile.rankedMatches < plugin.settings().provisionalMatches();

                int newWinnerElo = Ranking.newElo(winnerElo, loserElo, true,
                        plugin.settings().kFactor(), winnerProvisional);
                int newLoserElo = Ranking.newElo(loserElo, winnerElo, false,
                        plugin.settings().kFactor(), loserProvisional);
                deltaWinner = newWinnerElo - winnerElo;
                deltaLoser = newLoserElo - loserElo;

                winnerProfile.setModeElo(mode.id(), newWinnerElo);
                loserProfile.setModeElo(mode.id(), newLoserElo);
                winnerProfile.elo = Math.max(0, winnerProfile.elo + deltaWinner);
                loserProfile.elo = Math.max(0, loserProfile.elo + deltaLoser);
                winnerProfile.rankedMatches++;
                loserProfile.rankedMatches++;
            }

            winnerProfile.addHistory(formatHistory(mode, loser == null ? "?" : loser.getName(), "胜", deltaWinner, match));
            loserProfile.addHistory(formatHistory(mode, winner == null ? "?" : winner.getName(), "负", deltaLoser, match));

            plugin.profiles().markDirty(winnerId);
            plugin.profiles().markDirty(loserId);
        }

        announce(match, winner, loser, reason, deltaWinner, deltaLoser);
        plugin.profiles().save(match.players()[0], true);
        plugin.profiles().save(match.players()[1], true);
    }

    private String formatHistory(GameMode mode, String opponent, String result, int delta, DuelMatch match) {
        String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date());
        String eloPart = match.ranked() ? (delta >= 0 ? " +" + delta : " " + delta) : "";
        return stamp + "|" + mode.id() + "|" + opponent + "|" + result + eloPart;
    }

    private void announce(DuelMatch match, Player winner, Player loser, String reason,
                          int deltaWinner, int deltaLoser) {
        GameMode mode = match.mode();
        if (match.winner() == null) {
            for (UUID uuid : match.players()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    plugin.messages().send(player, "duel.draw");
                }
            }
            return;
        }

        if (winner != null) {
            plugin.messages().send(winner, "duel.won",
                    "loser", loser == null ? "?" : loser.getName(), "reason", reason);
            match.title(winner, "<gradient:#00ffa3:#00d4ff><bold>VICTORY</bold></gradient>",
                    "<gray>击败了 <white>" + (loser == null ? "?" : loser.getName()), 4);
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            spawnFirework(winner.getLocation());
            if (match.ranked()) {
                winner.sendMessage(plugin.messages().prefixed("stats.line",
                        "label", "排位分", "value", (deltaWinner >= 0 ? "<green>+" : "<red>") + deltaWinner
                                + " <dark_gray>(" + plugin.profiles().get(winner.getUniqueId())
                                .modeElo(mode.id(), plugin.settings().startElo()) + ")"));
            }
        }
        if (loser != null) {
            plugin.messages().send(loser, "duel.lost",
                    "winner", winner == null ? "?" : winner.getName(), "reason", reason);
            match.title(loser, "<gradient:#ff4d6d:#ff8fa3><bold>DEFEAT</bold></gradient>",
                    "<gray>被 <white>" + (winner == null ? "?" : winner.getName()) + " <gray>击败", 4);
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 0.8f, 0.8f);
            if (match.ranked()) {
                loser.sendMessage(plugin.messages().prefixed("stats.line",
                        "label", "排位分", "value", (deltaLoser >= 0 ? "<green>+" : "<red>") + deltaLoser
                                + " <dark_gray>(" + plugin.profiles().get(loser.getUniqueId())
                                .modeElo(mode.id(), plugin.settings().startElo()) + ")"));
            }
        }

        if (plugin.settings().broadcastResults() && winner != null) {
            plugin.messages().broadcast("duel.broadcast-win",
                    "winner", winner.getName(), "loser", loser == null ? "?" : loser.getName(),
                    "mode", mode.display(), "reason", reason);
        }
        match.effectsEnd();
    }

    private void spawnFirework(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Firework firework = location.getWorld().spawn(location, Firework.class, fw -> {
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.fromRGB(0x00FFA3), Color.fromRGB(0x00D4FF))
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withTrail()
                            .withFlicker()
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                });
                firework.setTicksLived(1);
            } catch (Exception ignored) {
            }
        }, 2L);
    }

    /* -------------------------------------------------------------- spectators */

    public void addSpectator(Player spectator, DuelMatch match) {
        if (spectators.containsKey(spectator.getUniqueId())) {
            return;
        }
        spectators.put(spectator.getUniqueId(), new SpectatorState(spectator.getLocation(),
                spectator.getGameMode(), match));
        spectator.setGameMode(org.bukkit.GameMode.SPECTATOR);
        match.addSpectator(spectator);
    }

    public void removeSpectator(Player spectator, boolean teleportBack) {
        SpectatorState state = spectators.remove(spectator.getUniqueId());
        if (state == null) {
            return;
        }
        state.match().removeSpectator(spectator.getUniqueId());
        if (teleportBack && spectator.isOnline()) {
            try {
                spectator.setGameMode(state.gameMode());
                spectator.teleport(state.location());
                spectator.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            } catch (Exception ignored) {
            }
            plugin.messages().send(spectator, "duel.spectate-stop");
        }
    }

    public void notifySpectators(DuelMatch match) {
        for (UUID uuid : new ArrayList<>(spectators.keySet())) {
            SpectatorState state = spectators.get(uuid);
            if (state == null || !state.match().equals(match)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player, true);
            } else {
                spectators.remove(uuid);
            }
        }
    }

    /* ------------------------------------------------------------------ state */

    public boolean isBusy(Player player) {
        UUID uuid = player.getUniqueId();
        return byPlayer.containsKey(uuid) || spectators.containsKey(uuid) || plugin.queues().inQueue(uuid);
    }

    public boolean inMatch(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.containsKey(player.getUniqueId());
    }

    public DuelMatch matchOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public DuelMatch matchOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public DuelMatch matchOf(Arena arena) {
        for (DuelMatch match : matches.values()) {
            if (match.arena().equals(arena)) {
                return match;
            }
        }
        return null;
    }

    public Collection<DuelMatch> active() {
        return matches.values();
    }

    public int activeCount() {
        return matches.size();
    }

    public List<DuelMatch> matchesOf(Player player) {
        List<DuelMatch> list = new ArrayList<>();
        DuelMatch match = matchOf(player);
        if (match != null) {
            list.add(match);
        }
        return list;
    }

    public void remove(DuelMatch match) {
        matches.remove(match.matchId());
        for (UUID uuid : match.players()) {
            byPlayer.remove(uuid);
        }
    }

    public void clearSession(UUID uuid) {
        byPlayer.remove(uuid);
    }

    /** 关闭服务器：强制结束并还原所有对局。 */
    public void shutdown() {
        for (DuelMatch match : new ArrayList<>(matches.values())) {
            for (UUID uuid : match.players()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                try {
                    plugin.snapshots().restore(player, match.snapshot(uuid));
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                } catch (Exception e) {
                    plugin.getLogger().severe("关闭时还原 " + player.getName() + " 失败: " + e.getMessage());
                }
            }
            plugin.arenas().release(match.arena());
        }
        matches.clear();
        byPlayer.clear();
        for (UUID uuid : new ArrayList<>(spectators.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player, true);
            }
        }
        spectators.clear();
    }

    public record SpectatorState(Location location, org.bukkit.GameMode gameMode, DuelMatch match) {
    }

    /** 给 /duel stats 的概要文字。 */
    public static String summarize(DuelMatch match) {
        return match.mode().display() + " @ #" + match.arena().id() + " (" + match.state() + " " + Text.formatTime(match.durationSeconds()) + ")";
    }
}
