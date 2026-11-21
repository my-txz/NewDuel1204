package com.newduel1204.command;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.GameMode;
import com.newduel1204.ui.Menus;
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
 * /queue 匹配队列命令。
 */
public final class QueueCommand implements CommandExecutor, TabCompleter {

    private final NewDuel1204 plugin;

    public QueueCommand(NewDuel1204 plugin) {
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
            plugin.menus().openModeSelect(player, Menus.Purpose.QUEUE_NORMAL, null);
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("leave") || first.equals("quit") || first.equals("离开")) {
            if (!plugin.queues().inQueue(player.getUniqueId())) {
                plugin.messages().send(player, "queue.not-queued");
                return true;
            }
            plugin.queues().leave(player.getUniqueId(), true);
            return true;
        }

        Optional<GameMode> parsed = GameMode.byId(first);
        GameMode mode;
        if (parsed.isEmpty()) {
            List<GameMode> fuzzy = GameMode.search(first);
            if (fuzzy.isEmpty()) {
                plugin.messages().send(player, "general.unknown-mode", "mode", args[0]);
                return true;
            }
            mode = fuzzy.get(0);
        } else {
            mode = parsed.get();
        }

        boolean ranked = args.length > 1 && (args[1].equalsIgnoreCase("ranked")
                || args[1].equalsIgnoreCase("排位") || args[1].equalsIgnoreCase("true"));
        if (plugin.queues().inQueue(player.getUniqueId())) {
            plugin.queues().leave(player.getUniqueId(), false);
        }
        plugin.queues().join(player, mode, ranked);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("leave".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("leave");
            }
            for (GameMode mode : GameMode.values()) {
                if (mode.id().startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(mode.id());
                }
            }
        } else if (args.length == 2) {
            for (String option : List.of("ranked", "normal")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        }
        return out;
    }

    /** 给 /queue list 提供可用模式名。 */
    public static List<String> modeIds() {
        return GameMode.ids();
    }
}
