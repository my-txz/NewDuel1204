package com.newduel1204.listener;

import com.newduel1204.NewDuel1204;
import com.newduel1204.match.DuelMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 上线 / 下线处理。这里是"崩溃恢复"真正生效的地方：
 * 玩家带着未还原的快照重新进服时，会先把真实数据还给他，再允许一切操作。
 */
public final class PlayerListener implements Listener {

    private final NewDuel1204 plugin;

    public PlayerListener(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.profiles().get(player);
        plugin.profiles().markDirty(player.getUniqueId());

        if (plugin.snapshots().hasPending(player.getUniqueId())) {
            // 延后 2 tick：等玩家的背包/世界完全同步后再还原，避免被客户端覆盖
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getLogger().info("正在为 " + player.getName() + " 还原未完成的快照…");
                if (plugin.snapshots().recover(player)) {
                    plugin.messages().send(player, "duel.restored");
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DuelMatch match = plugin.matches().matchOf(player);
        if (match != null) {
            match.onQuit(player);
        }
        plugin.queues().leave(player.getUniqueId(), false);
        if (plugin.matches().isSpectator(player)) {
            plugin.matches().removeSpectator(player, false);
        }
        plugin.profiles().save(player.getUniqueId(), false);
    }
}
