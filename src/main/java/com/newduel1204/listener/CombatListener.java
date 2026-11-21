package com.newduel1204.listener;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.CombatRule;
import com.newduel1204.match.DuelMatch;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 战斗规则执行者：伤害归属、特殊模式判定、死亡与重生处理。
 */
public final class CombatListener implements Listener {

    private final NewDuel1204 plugin;

    public CombatListener(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    /* ---------------------------------------------------------------- 伤害归属 */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        DuelMatch victimMatch = plugin.matches().matchOf(victim);

        Player attacker = resolveAttacker(event.getDamager());

        if (victimMatch == null) {
            // 受害者不在对局中：如果在竞技场世界被玩家攻击，直接保护
            if (attacker != null && plugin.arenas().world() != null
                    && victim.getWorld().equals(plugin.arenas().world())) {
                event.setCancelled(true);
            }
            return;
        }

        if (attacker == null) {
            // 环境伤害（水晶爆炸的施放者已丢失等）保持原样，交回原版处理
            return;
        }

        DuelMatch attackerMatch = plugin.matches().matchOf(attacker);
        if (attackerMatch == null || !attackerMatch.equals(victimMatch)) {
            // 旁观者 / 外人不能打到对局中的玩家
            event.setCancelled(true);
            return;
        }
        if (victimMatch.isParticipant(attacker.getUniqueId()) && attacker.equals(victim)) {
            return;
        }

        CombatRule rule = victimMatch.mode().combat();
        switch (rule) {
            case NO_DAMAGE -> {
                // 相扑 / 拳击：不吃伤害，但保留击退与命中统计
                event.setCancelled(true);
                applyKnockback(attacker, victim);
                victimMatch.onDamage(attacker.getUniqueId(), victim.getUniqueId(), 0);
                victim.playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 1.2f);
            }
            case ONE_HIT -> {
                event.setDamage(1000);
                victimMatch.onDamage(attacker.getUniqueId(), victim.getUniqueId(), 1000);
            }
            default -> {
                double finalDamage = event.getDamage();
                if (victimMatch.mode().comboLaunch()) {
                    launch(victim);
                }
                victimMatch.onDamage(attacker.getUniqueId(), victim.getUniqueId(), finalDamage);
            }
        }
    }

    /** 从投射物反推真正的攻击者。 */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof Fireball fireball && fireball.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof EnderCrystal crystal && crystal.getBeamTarget() != null) {
            return null;
        }
        return null;
    }

    private void applyKnockback(Player attacker, Player victim) {
        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() < 0.0001) {
            direction = attacker.getLocation().getDirection();
        }
        direction.setY(0).normalize().multiply(attacker.isSprinting() ? 0.62 : 0.44).setY(0.36);
        victim.setVelocity(direction);
    }

    private void launch(Player victim) {
        Vector velocity = victim.getVelocity();
        victim.setVelocity(velocity.clone().setY(Math.max(0.42, velocity.getY())));
    }

    /* -------------------------------------------------------------- 环境伤害 */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelMatch match = plugin.matches().matchOf(player);
        if (match == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.teleport(match.arena().spawnA());
            match.end(match.other(player.getUniqueId()),
                    com.newduel1204.match.EndReason.RING_OUT);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelMatch match = plugin.matches().matchOf(player);
        if (match == null || match.mode().naturalRegen()) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED
                || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /* -------------------------------------------------------------- 死亡重生 */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DuelMatch match = plugin.matches().matchOf(player);
        if (match == null) {
            return;
        }
        // 绝对禁止掉落：物品与经验全部保留，由快照还原流程接管
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        event.deathMessage(null);

        match.onDeath(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        DuelMatch match = plugin.matches().matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        event.setRespawnLocation(match.arena().spawnA());
    }

    /* ---------------------------------------------------------------- 消耗品 */

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        DuelMatch match = plugin.matches().matchOf(event.getPlayer());
        if (match == null || !match.mode().soupHeal()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item.getType() != Material.MUSHROOM_STEW) {
            return;
        }
        Player player = event.getPlayer();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
                ? 20 : player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + 7.0));
        // 无限续汤，保证对局节奏
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.getInventory().addItem(new ItemStack(Material.MUSHROOM_STEW));
            }
        });
    }
}
