package com.newduel1204.command;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.GameMode;
import com.newduel1204.match.DuelMatch;
import com.newduel1204.match.DuelRequest;
import com.newduel1204.util.Text;
import net.kyori.adventure.text.Component;
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
import java.util.Optional;

/**
 * /duel 主命令。
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final NewDuel1204 plugin;

    public DuelCommand(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("newduel.use")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept", "接受" -> {
                accept(player, args.length > 1 ? args[1] : null);
                return true;
            }
            case "deny", "decline", "拒绝" -> {
                deny(player, args.length > 1 ? args[1] : null);
                return true;
            }
            case "leave", "quit", "离开" -> {
                leave(player);
                return true;
            }
            case "stats", "战绩" -> {
                if (args.length > 1) {
                    OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (target == null) {
                        plugin.messages().send(player, "general.unknown-player", "player", args[1]);
                        return true;
                    }
                    plugin.menus().openStats(player, target);
                } else {
                    plugin.menus().openStats(player, player);
                }
                return true;
            }
            case "top", "排行" -> {
                plugin.menus().openTop(player, args.length > 1 ? args[1] : null);
                return true;
            }
            case "modes", "模式" -> {
                sendModeList(player);
                return true;
            }
            case "spectate", "观战" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    plugin.matches().removeSpectator(player, true);
                    return true;
                }
                if (args.length > 1) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        plugin.messages().send(player, "general.player-offline", "player", args[1]);
                        return true;
                    }
                    DuelMatch match = plugin.matches().matchOf(target);
                    if (match == null) {
                        plugin.messages().send(player, "duel.spectate-not-in-match");
                        return true;
                    }
                    plugin.matches().addSpectator(player, match);
                    plugin.messages().send(player, "duel.spectate-start", "player", target.getName());
                } else {
                    plugin.menus().openSpectate(player);
                }
                return true;
            }
            case "menu", "菜单" -> {
                plugin.menus().openMain(player);
                return true;
            }
            case "rematch", "再来一局" -> {
                rematch(player);
                return true;
            }
            case "help", "帮助", "?" -> {
                sendHelp(player);
                return true;
            }
            default -> {
                // /duel <player> [mode]
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    plugin.messages().send(player, "general.unknown-player", "player", args[0]);
                    return true;
                }
                GameMode mode = GameMode.byId(plugin.preferredMode(player.getUniqueId()).id()).orElse(GameMode.NODEBUFF);
                if (args.length > 1) {
                    Optional<GameMode> parsed = GameMode.byId(args[1]);
                    if (parsed.isEmpty()) {
                        List<GameMode> fuzzy = GameMode.search(args[1]);
                        if (fuzzy.size() == 1) {
                            mode = fuzzy.get(0);
                        } else {
                            plugin.messages().send(player, "general.unknown-mode", "mode", args[1]);
                            return true;
                        }
                    } else {
                        mode = parsed.get();
                    }
                }
                invite(player, target, mode);
                return true;
            }
        }
    }

    private void invite(Player sender, Player target, GameMode mode) {
        if (sender.equals(target)) {
            plugin.messages().send(sender, "general.self-target");
            return;
        }
        if (plugin.matches().isBusy(sender)) {
            plugin.messages().send(sender, "duel.busy");
            return;
        }
        if (plugin.matches().isBusy(target)) {
            plugin.messages().send(sender, "duel.target-busy", "player", target.getName());
            return;
        }
        plugin.preferredMode(sender.getUniqueId(), mode);
        plugin.requests().send(sender, target, mode);
        plugin.messages().send(sender, "duel.invite-sent", "player", target.getName(), "mode", mode.display());
        plugin.messages().send(target, "duel.invite-received", "player", sender.getName(), "mode", mode.display());
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private void accept(Player player, String senderName) {
        Optional<DuelRequest> optional = senderName == null
                ? plugin.requests().latest(player)
                : plugin.requests().find(player, senderName);
        if (optional.isEmpty()) {
            plugin.messages().send(player, "duel.no-invite");
            return;
        }
        DuelRequest request = optional.get();
        plugin.requests().remove(player, request.sender());

        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            plugin.messages().send(player, "general.player-offline", "player", request.senderName());
            return;
        }
        if (plugin.matches().isBusy(player) || plugin.matches().isBusy(sender)) {
            plugin.messages().send(player, "duel.busy");
            return;
        }
        plugin.matches().requestStart(sender, player, request.mode(), false);
        plugin.messages().send(player, "duel.accept-broadcast",
                "player", player.getName(), "target", sender.getName(), "mode", request.mode().display());
        plugin.messages().send(sender, "duel.accept-broadcast",
                "player", player.getName(), "target", sender.getName(), "mode", request.mode().display());
    }

    private void deny(Player player, String senderName) {
        Optional<DuelRequest> optional = senderName == null
                ? plugin.requests().latest(player)
                : plugin.requests().find(player, senderName);
        if (optional.isEmpty()) {
            plugin.messages().send(player, "duel.no-invite");
            return;
        }
        DuelRequest request = optional.get();
        plugin.requests().remove(player, request.sender());
        plugin.messages().send(player, "duel.invite-denied-target", "player", request.senderName());
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            plugin.messages().send(sender, "duel.invite-denied", "player", player.getName());
        }
    }

    private void leave(Player player) {
        DuelMatch match = plugin.matches().matchOf(player);
        if (match != null) {
            match.forfeit(player);
            plugin.messages().send(player, "duel.left-match");
            return;
        }
        if (plugin.matches().isSpectator(player)) {
            plugin.matches().removeSpectator(player, true);
            return;
        }
        if (plugin.queues().inQueue(player.getUniqueId())) {
            plugin.queues().leave(player.getUniqueId(), true);
            return;
        }
        plugin.messages().send(player, "queue.not-queued");
    }

    private void rematch(Player player) {
        DuelMatch match = plugin.matches().matchOf(player);
        if (match != null) {
            plugin.messages().send(player, "duel.busy");
            return;
        }
        Optional<DuelRequest> optional = plugin.requests().latest(player);
        if (optional.isPresent()) {
            accept(player, optional.get().senderName());
            return;
        }
        plugin.messages().send(player, "duel.no-invite");
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Text.mm("<gradient:#5ee7ff:#a06bff><bold>NewDuel1204</bold></gradient> <dark_gray>· <gray>帮助"));
        player.sendMessage(Text.mm("<white>/duel <gray>打开决斗菜单"));
        player.sendMessage(Text.mm("<white>/duel <玩家> [模式] <gray>发起挑战"));
        player.sendMessage(Text.mm("<white>/duel accept|deny [玩家] <gray>接受 / 拒绝邀请"));
        player.sendMessage(Text.mm("<white>/duel leave <gray>离开对局或队列"));
        player.sendMessage(Text.mm("<white>/duel spectate <玩家> <gray>观战"));
        player.sendMessage(Text.mm("<white>/duel stats [玩家] <gray>查看战绩"));
        player.sendMessage(Text.mm("<white>/duel top [模式] <gray>排行榜"));
        player.sendMessage(Text.mm("<white>/duel modes <gray>查看全部模式"));
        player.sendMessage(Text.mm("<white>/queue <模式> [ranked] <gray>加入匹配队列"));
        player.sendMessage(Component.empty());
    }

    private void sendModeList(Player player) {
        player.sendMessage(Text.mm("<gray>可用模式 <dark_gray>(" + GameMode.values().length + ")"));
        for (GameMode mode : GameMode.values()) {
            player.sendMessage(Text.mm("<dark_gray>· <click:run_command:'/queue " + mode.id() + "'>"
                    + "<white>" + mode.id() + " <dark_gray>- <gray>" + mode.display() + " " + mode.typeLabel()
                    + "</click>"));
        }
        player.sendMessage(Text.mm("<gray>提示：<white>/duel <玩家> <模式></white> 可以直接指定模式。"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("accept", "deny", "leave", "stats", "top", "spectate", "modes", "menu",
                    "rematch", "help")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("stats") || sub.equals("spectate") || sub.equals("accept") || sub.equals("deny")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(online.getName());
                    }
                }
            } else {
                for (GameMode mode : GameMode.values()) {
                    if (mode.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(mode.id());
                    }
                }
            }
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("stats")) {
            for (GameMode mode : GameMode.values()) {
                if (mode.id().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(mode.id());
                }
            }
        }
        return out;
    }
}
