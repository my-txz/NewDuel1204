package com.newduel1204.listener;

import com.newduel1204.NewDuel1204;
import com.newduel1204.arena.Arena;
import com.newduel1204.match.DuelMatch;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 竞技场世界保护 + 特殊道具（火焰弹）+ 方块改动回滚记录。
 */
public final class ArenaListener implements Listener {

    private final NewDuel1204 plugin;
    private final Map<UUID, Long> fireballCooldown = new HashMap<>();

    public ArenaListener(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    private boolean isArenaWorld(World world) {
        return plugin.arenas().world() != null && plugin.arenas().world().equals(world);
    }

    /* ---------------------------------------------------------------- 方块改动 */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        DuelMatch match = plugin.matches().matchOf(player);
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        if (!match.mode().allowBuild()) {
            event.setCancelled(true);
            player.sendActionBar(plugin.messages().get("general.disabled"));
            return;
        }
        Arena arena = match.arena();
        if (!arena.bounds().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        arena.recordBlock(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(),
                event.getBlockReplacedState().getBlockData());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        DuelMatch match = plugin.matches().matchOf(player);
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        if (!match.mode().allowBreak()) {
            event.setCancelled(true);
            return;
        }
        Arena arena = match.arena();
        if (!arena.bounds().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        BlockData previous = event.getBlock().getBlockData();
        arena.recordBlock(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(), previous);
        event.setDropItems(false);
    }

    /** 水晶 / 火焰弹爆炸：记录被炸掉的方块，方便对局结束后还原。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        if (!isArenaWorld(event.getLocation().getWorld())) {
            return;
        }
        Arena arena = plugin.arenas().arenaAt(event.getLocation());
        boolean buildAllowed = false;
        if (arena != null) {
            DuelMatch match = plugin.matches().matchOf(arena);
            buildAllowed = match != null && match.mode().allowBuild();
            if (!buildAllowed) {
                event.blockList().clear();
                return;
            }
            for (Block block : event.blockList()) {
                arena.recordBlock(block.getX(), block.getY(), block.getZ(), block.getBlockData());
            }
        } else {
            event.blockList().clear();
        }
    }

    /** 水/岩浆流动不能让液体流出竞技场。 */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!isArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        Arena arena = plugin.arenas().arenaAt(event.getToBlock().getLocation());
        if (arena == null) {
            event.setCancelled(true);
        }
    }

    /* ---------------------------------------------------------------- 物品限制 */

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.settings().disableItemDrop()) {
            return;
        }
        DuelMatch match = plugin.matches().matchOf(event.getPlayer());
        if (match != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(plugin.messages().get("general.disabled"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isArenaWorld(player.getWorld())) {
            return;
        }
        if (plugin.matches().matchOf(player) == null) {
            event.setCancelled(true);
        }
    }

    /* -------------------------------------------------------------- 火焰弹道具 */

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIRE_CHARGE) {
            return;
        }
        DuelMatch match = plugin.matches().matchOf(event.getPlayer());
        if (match == null || !match.mode().fireball()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long last = fireballCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 700L) {
            return;
        }
        fireballCooldown.put(player.getUniqueId(), now);

        Vector direction = player.getLocation().getDirection().normalize();
        Location spawn = player.getEyeLocation().add(direction.clone().multiply(1.2));
        player.getWorld().spawn(spawn, LargeFireball.class, fireball -> {
            fireball.setDirection(direction);
            fireball.setYield(1.4f);
            fireball.setIsIncendiary(false);
            fireball.setShooter(player);
            fireball.setVelocity(direction.multiply(1.6));
        });
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1.2f);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            int amount = item.getAmount();
            if (amount <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(amount - 1);
            }
        }
    }

    /* -------------------------------------------------------------- 命令与传送 */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.matches().matchOf(player) == null) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        String label = message.split(" ")[0];
        if (plugin.settings().allowedCommands().contains(label)) {
            return;
        }
        if (plugin.settings().blockedCommands().contains(label) || plugin.settings().blockCommands()) {
            event.setCancelled(true);
            player.sendActionBar(plugin.messages().get("general.disabled"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isArenaWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && isArenaWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** 清理过期冷却记录，避免内存增长。 */
    public void pruneCooldowns() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = fireballCooldown.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 60_000L) {
                it.remove();
            }
        }
    }
}
