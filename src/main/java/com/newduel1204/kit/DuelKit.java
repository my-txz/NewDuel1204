package com.newduel1204.kit;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 一套不可变的出战装备。所有对局都通过 {@link #apply(Player)} 原子地替换玩家状态，
 * 原状态由快照系统负责保存与还原。
 */
public final class DuelKit {

    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final List<PotionEffect> effects;
    private final double maxHealth;
    private final int foodLevel;
    private final double attackSpeed;

    DuelKit(ItemStack[] inventory, ItemStack[] armor, ItemStack offhand, List<PotionEffect> effects,
            double maxHealth, int foodLevel, double attackSpeed) {
        this.inventory = inventory;
        this.armor = armor;
        this.offhand = offhand;
        this.effects = effects;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.attackSpeed = attackSpeed;
    }

    public void apply(Player player) {
        var inv = player.getInventory();
        inv.clear();
        inv.setContents(cloneAll(inventory));
        inv.setArmorContents(cloneAll(armor));
        inv.setItemInOffHand(offhand == null ? null : offhand.clone());

        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setRemainingAir(player.getMaximumAir());
        player.setFoodLevel(foodLevel);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setLevel(0);
        player.setExp(0f);

        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(maxHealth, 20.0));

        var speedAttr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(attackSpeed > 0 ? attackSpeed : 4.0);
        }

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        player.updateInventory();
    }

    /** 箱子/物品形式的预览内容（用于模式选择菜单）。 */
    public List<ItemStack> previewItems() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item != null && item.getType() != Material.AIR) {
                list.add(item.clone());
            }
        }
        for (ItemStack item : armor) {
            if (item != null && item.getType() != Material.AIR) {
                list.add(item.clone());
            }
        }
        if (offhand != null) {
            list.add(offhand.clone());
        }
        return list;
    }

    public List<PotionEffect> effects() {
        return effects;
    }

    private static ItemStack[] cloneAll(ItemStack[] source) {
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    /** 便捷构造：给一个"永久"时长（超过任何一局的最大时长）。 */
    public static PotionEffect infinite(PotionEffectType type, int amplifier) {
        return new PotionEffect(type, 20 * 60 * 30, amplifier, false, false, true);
    }

    public static PotionEffect timed(PotionEffectType type, int seconds, int amplifier) {
        return new PotionEffect(type, 20 * seconds, amplifier, false, true, true);
    }
}
