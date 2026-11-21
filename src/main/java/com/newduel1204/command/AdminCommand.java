package com.newduel1204.command;

import com.newduel1204.NewDuel1204;
import com.newduel1204.arena.Arena;
import com.newduel1204.match.DuelMatch;
import com.newduel1204.player.PlayerProfile;
import com.newduel1204.player.Ranking;
import com.newduel1204.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /nd 管理命令。
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final NewDuel1204 plugin;

    public AdminCommand(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("newduel.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                long start = System.currentTimeMillis();
                plugin.reloadAll();
                plugin.messages().send(sender, "general.reloaded",
                        "ms", String.valueOf(System.currentTimeMillis() - start));
            }
            case "version", "ver" -> plugin.messages().send(sender, "admin.version",
                    "version", plugin.getPluginMeta().getVersion());
            case "arena", "arenas" -> arena(sender, args);
            case "snapshot", "snapshots" -> snapshot(sender, args);
            case "profile", "data" -> profile(sender, args);
            case "season" -> season(sender, args);
            case "stop" -> stop(sender, args);
            case "info", "status" -> info(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.mm("<gradient:#5ee7ff:#a06bff><bold>NewDuel1204</bold></gradient> <gray>管理命令"));
        sender.sendMessage(Text.mm("<white>/nd reload <gray>重载配置与语言"));
        sender.sendMessage(Text.mm("<white>/nd arena list|tp|rebuild|release <gray>竞技场管理"));
        sender.sendMessage(Text.mm("<white>/nd snapshot list|recover <player> <gray>快照管理"));
        sender.sendMessage(Text.mm("<white>/nd profile info|reset <player> <gray>玩家数据"));
        sender.sendMessage(Text.mm("<white>/nd season reset <gray>重置赛季"));
        sender.sendMessage(Text.mm("<white>/nd stop <player> <gray>强制结束某玩家的对局"));
        sender.sendMessage(Text.mm("<white>/nd info <gray>运行状态"));
        sender.sendMessage(Text.mm("<white>/nd version <gray>版本信息"));
    }

    private void arena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                plugin.messages().send(sender, "admin.arena-list-header",
                        "count", String.valueOf(plugin.arenas().arenas().size()));
                for (Arena arena : plugin.arenas().arenas()) {
                    sender.sendMessage(plugin.messages().get("admin.arena-line",
                            "id", String.valueOf(arena.id()),
                            "kind", arena.kind().displayName(),
                            "state", arena.stateColor() + arena.state().display() + "</" + closeTag(arena) + ">",
                            "pos", arena.posString()));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.player-only");
                    return;
                }
                Arena arena = parseArena(sender, args);
                if (arena == null) {
                    return;
                }
                player.teleport(arena.center().add(0, 1, 0));
                plugin.messages().send(sender, "admin.arena-tp", "id", String.valueOf(arena.id()));
            }
            case "rebuild" -> {
                Arena arena = parseArena(sender, args);
                if (arena == null) {
                    return;
                }
                plugin.arenas().rebuild(arena);
                plugin.messages().send(sender, "admin.arena-rebuilt", "id", String.valueOf(arena.id()));
            }
            case "release" -> {
                Arena arena = parseArena(sender, args);
                if (arena == null) {
                    return;
                }
                plugin.arenas().release(arena);
                plugin.messages().send(sender, "admin.arena-rebuilt", "id", String.valueOf(arena.id()));
            }
            default -> help(sender);
        }
    }

    private String closeTag(Arena arena) {
        return switch (arena.state()) {
            case IDLE -> "green";
            case RESERVED -> "yellow";
            case IN_USE -> "red";
        };
    }

    private Arena parseArena(CommandSender sender, String[] args) {
        if (args.length < 3) {
            help(sender);
            return null;
        }
        try {
            Arena arena = plugin.arenas().arena(Integer.parseInt(args[2]));
            if (arena == null) {
                plugin.messages().send(sender, "admin.arena-unknown", "id", args[2]);
            }
            return arena;
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "admin.arena-unknown", "id", args[2]);
            return null;
        }
    }

    private void snapshot(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            Map<String, Long> stats = plugin.snapshots().stats();
            plugin.messages().send(sender, "admin.snapshot-list",
                    "count", String.valueOf(stats.getOrDefault("pending", 0L)),
                    "detail", " <dark_gray>(备份 " + stats.getOrDefault("backups", 0L) + " 份)</dark_gray>");
            if (stats.getOrDefault("pending", 0L) == 0) {
                plugin.messages().send(sender, "admin.snapshot-none");
            } else {
                for (java.util.UUID uuid : plugin.snapshots().pendingIds()) {
                    sender.sendMessage(Text.mm("<dark_gray>· <white>" + uuid));
                }
            }
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("recover")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messages().send(sender, "general.player-offline", "player", args[2]);
                return;
            }
            if (!plugin.snapshots().hasPending(target.getUniqueId())) {
                plugin.messages().send(sender, "admin.snapshot-none");
                return;
            }
            plugin.snapshots().recover(target);
            plugin.messages().send(sender, "admin.snapshot-recovered", "player", target.getName());
            return;
        }
        help(sender);
    }

    private void profile(CommandSender sender, String[] args) {
        if (args.length < 3) {
            help(sender);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "general.unknown-player", "player", args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            plugin.profiles().reset(target.getUniqueId());
            plugin.messages().send(sender, "admin.profile-reset",
                    "player", target.getName() == null ? args[2] : target.getName());
            return;
        }
        if (args[1].equalsIgnoreCase("info")) {
            PlayerProfile profile = plugin.profiles().get(target.getUniqueId());
            sender.sendMessage(Text.mm("<gray>玩家 <white>" + profile.name + "</white> <dark_gray>(" + profile.uuid + ")"));
            sender.sendMessage(Text.mm("<gray>段位 <white>" + Ranking.plainDivision(profile.elo)
                    + "</white> 分数 <white>" + profile.elo + "</white> 胜 <green>" + profile.wins
                    + "</green> 负 <red>" + profile.losses));
            sender.sendMessage(Text.mm("<gray>历史 <dark_gray>" + profile.history.size() + " 条"));
            return;
        }
        help(sender);
    }

    private void season(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            plugin.profiles().resetSeason();
            plugin.messages().send(sender, "admin.season-reset",
                    "season", String.valueOf(plugin.settings().season()));
            return;
        }
        help(sender);
    }

    private void stop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-offline", "player", args[1]);
            return;
        }
        DuelMatch match = plugin.matches().matchOf(target);
        if (match == null) {
            plugin.messages().send(sender, "duel.spectate-not-in-match");
            return;
        }
        match.adminStop();
        sender.sendMessage(Text.mm("<green>已强制结束该对局。"));
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Text.mm("<gradient:#5ee7ff:#a06bff><bold>NewDuel1204</bold></gradient> <gray>运行状态"));
        sender.sendMessage(Text.mm("<gray>竞技场世界: <white>"
                + (plugin.arenas().world() == null ? "未创建" : plugin.arenas().world().getName())));
        sender.sendMessage(Text.mm("<gray>预生成进度: <white>" + plugin.arenas().pregenDone()
                + "/" + plugin.arenas().pregenTotal() + " <dark_gray>(ready=" + plugin.arenas().ready() + ")"));
        sender.sendMessage(Text.mm("<gray>竞技场: <white>" + plugin.arenas().idleArenas().size()
                + " 空闲</white> / <white>" + plugin.arenas().arenas().size() + " 总数"));
        sender.sendMessage(Text.mm("<gray>进行中对局: <white>" + plugin.matches().activeCount()));
        sender.sendMessage(Text.mm("<gray>队列人数: <white>" + plugin.queues().size()
                + "</white> 待处理邀请: <white>" + plugin.requests().size()));
        sender.sendMessage(Text.mm("<gray>玩家档案: <white>" + plugin.profiles().loadedCount()
                + "</white> 未还原快照: <white>" + plugin.snapshots().pendingCount()));
        sender.sendMessage(Text.mm("<gray>TPS: <white>" + String.format("%.2f",
                Bukkit.getTPS()[0])));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("reload", "arena", "snapshot", "profile", "season", "stop", "info", "version")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "arena" -> out.addAll(List.of("list", "tp", "rebuild", "release"));
                case "snapshot" -> out.addAll(List.of("list", "recover"));
                case "profile" -> out.addAll(List.of("info", "reset"));
                case "season" -> out.add("reset");
                case "stop" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> {
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("arena")) {
                for (Arena arena : plugin.arenas().arenas()) {
                    out.add(String.valueOf(arena.id()));
                }
            } else {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        }
        return out;
    }
}
