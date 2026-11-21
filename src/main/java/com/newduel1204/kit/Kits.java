package com.newduel1204.kit;

import com.newduel1204.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有模式预置装备。目标是"开箱即用"：服主不需要配置任何 kit 文件。
 */
public final class Kits {

    /** 全局攻击速度（1.8 手感默认 16.0，可在配置文件里改成 4.0 使用 1.9 冷却）。 */
    private static double globalAttackSpeed = 16.0;

    private Kits() {
    }

    public static void configure(double attackSpeed) {
        globalAttackSpeed = Math.max(0.5, attackSpeed);
    }

    /* --------------------------------------------------------------- 建造器 */

    public static final class Builder {
        private final ItemStack[] inv = new ItemStack[36];
        private ItemStack[] armor = new ItemStack[4];
        private ItemStack offhand;
        private final List<PotionEffect> effects = new ArrayList<>();
        private double health = 20.0;
        private int food = 20;
        private double attackSpeed = 0;

        public Builder set(int slot, ItemStack item) {
            if (slot >= 0 && slot < 36) {
                inv[slot] = item;
            }
            return this;
        }

        /** 依序传入 头盔、胸甲、护腿、靴子。 */
        public Builder armor(ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
            armor = new ItemStack[]{boots, legs, chest, helmet};
            return this;
        }

        public Builder offhand(ItemStack item) {
            this.offhand = item;
            return this;
        }

        public Builder effect(PotionEffect effect) {
            effects.add(effect);
            return this;
        }

        public Builder health(double health) {
            this.health = health;
            return this;
        }

        public Builder food(int food) {
            this.food = food;
            return this;
        }

        public Builder attackSpeed(double speed) {
            this.attackSpeed = speed;
            return this;
        }

        public DuelKit build() {
            return new DuelKit(inv, armor, offhand, List.copyOf(effects), health, food,
                    attackSpeed > 0 ? attackSpeed : globalAttackSpeed);
        }
    }

    /* ------------------------------------------------------------ 物品工具 */

    public static ItemStack item(Material material) {
        return new ItemStack(material);
    }

    public static ItemStack item(Material material, int amount) {
        return new ItemStack(material, Math.max(1, amount));
    }

    public static ItemStack named(ItemStack stack, String miniName, String... lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(miniName));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Text.mm(line));
                }
                meta.lore(lines);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack ench(ItemStack stack, Enchantment enchantment, int level) {
        stack.addUnsafeEnchantment(enchantment, level);
        return stack;
    }

    public static ItemStack armorPiece(Material material, int protLevel) {
        ItemStack stack = new ItemStack(material);
        if (protLevel > 0) {
            ench(stack, Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
        }
        ench(stack, Enchantment.DURABILITY, 3);
        return stack;
    }

    public static ItemStack armorPieceBlast(Material material, int blastLevel) {
        ItemStack stack = new ItemStack(material);
        ench(stack, Enchantment.PROTECTION_EXPLOSIONS, blastLevel);
        ench(stack, Enchantment.PROTECTION_ENVIRONMENTAL, blastLevel);
        ench(stack, Enchantment.DURABILITY, 3);
        return stack;
    }

    public static ItemStack sword(Material material, int sharp, boolean fireAspect) {
        ItemStack stack = new ItemStack(material);
        if (sharp > 0) {
            ench(stack, Enchantment.DAMAGE_ALL, sharp);
        }
        if (fireAspect) {
            ench(stack, Enchantment.FIRE_ASPECT, 1);
        }
        ench(stack, Enchantment.DURABILITY, 3);
        return stack;
    }

    public static ItemStack bow(int power, int punch) {
        ItemStack stack = new ItemStack(Material.BOW);
        ench(stack, Enchantment.ARROW_DAMAGE, power);
        if (punch > 0) {
            ench(stack, Enchantment.ARROW_KNOCKBACK, punch);
        }
        ench(stack, Enchantment.DURABILITY, 3);
        return stack;
    }

    public static ItemStack potion(PotionType type, int amount) {
        ItemStack stack = new ItemStack(Material.SPLASH_POTION, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(type);
            potionMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(potionMeta);
        }
        return stack;
    }

    public static ItemStack gapples(int amount) {
        return item(Material.GOLDEN_APPLE, amount);
    }

    /* ------------------------------------------------------------------ kits */

    public static DuelKit noDebuff() {
        return new Builder()
                .armor(armorPiece(Material.DIAMOND_HELMET, 4), armorPiece(Material.DIAMOND_CHESTPLATE, 4),
                        armorPiece(Material.DIAMOND_LEGGINGS, 4), armorPiece(Material.DIAMOND_BOOTS, 4))
                .set(0, sword(Material.DIAMOND_SWORD, 5, false))
                .set(1, potion(PotionType.INSTANT_HEAL, 2))
                .set(2, item(Material.COOKED_BEEF, 16))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit debuff() {
        return new Builder()
                .armor(armorPiece(Material.DIAMOND_HELMET, 4), armorPiece(Material.DIAMOND_CHESTPLATE, 4),
                        armorPiece(Material.DIAMOND_LEGGINGS, 4), armorPiece(Material.DIAMOND_BOOTS, 4))
                .set(0, sword(Material.DIAMOND_SWORD, 5, false))
                .set(1, potion(PotionType.INSTANT_HEAL, 2))
                .set(2, potion(PotionType.POISON, 2))
                .set(3, potion(PotionType.SLOWNESS, 2))
                .set(4, potion(PotionType.STRONG_HARMING, 1))
                .set(5, item(Material.COOKED_BEEF, 16))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit classic() {
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 1), armorPiece(Material.IRON_CHESTPLATE, 1),
                        armorPiece(Material.IRON_LEGGINGS, 1), armorPiece(Material.IRON_BOOTS, 1))
                .set(0, sword(Material.IRON_SWORD, 1, false))
                .set(1, bow(1, 0))
                .set(2, item(Material.ARROW, 16))
                .set(3, gapples(2))
                .build();
    }

    public static DuelKit gapple() {
        return new Builder()
                .armor(armorPiece(Material.DIAMOND_HELMET, 2), armorPiece(Material.DIAMOND_CHESTPLATE, 2),
                        armorPiece(Material.DIAMOND_LEGGINGS, 2), armorPiece(Material.DIAMOND_BOOTS, 2))
                .set(0, sword(Material.DIAMOND_SWORD, 3, false))
                .set(1, gapples(32))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 0))
                .build();
    }

    public static DuelKit soup() {
        Builder builder = new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 2), armorPiece(Material.IRON_CHESTPLATE, 2),
                        armorPiece(Material.IRON_LEGGINGS, 2), armorPiece(Material.IRON_BOOTS, 2))
                .set(0, sword(Material.IRON_SWORD, 2, false))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1));
        for (int i = 1; i <= 8; i++) {
            builder.set(i, named(item(Material.MUSHROOM_STEW), "<gold>蘑菇汤 <gray>(右键回复 3.5 颗心)"));
        }
        builder.set(9, gapples(1));
        return builder.build();
    }

    public static DuelKit sumo() {
        return new Builder()
                .armor(null, null, null, null)
                .build();
    }

    public static DuelKit boxing() {
        return new Builder()
                .armor(null, null, null, null)
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit archer() {
        return new Builder()
                .armor(armorPiece(Material.LEATHER_HELMET, 2), armorPiece(Material.LEATHER_CHESTPLATE, 2),
                        armorPiece(Material.LEATHER_LEGGINGS, 2), armorPiece(Material.LEATHER_BOOTS, 2))
                .set(0, bow(3, 1))
                .set(1, item(Material.ARROW, 32))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit buildUhc() {
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 2), armorPiece(Material.IRON_CHESTPLATE, 2),
                        armorPiece(Material.IRON_LEGGINGS, 2), armorPiece(Material.IRON_BOOTS, 2))
                .set(0, sword(Material.DIAMOND_SWORD, 3, false))
                .set(1, bow(3, 0))
                .set(2, item(Material.ARROW, 32))
                .set(3, item(Material.OAK_PLANKS, 64))
                .set(4, item(Material.WATER_BUCKET))
                .set(5, item(Material.LAVA_BUCKET))
                .set(6, gapples(8))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 0))
                .build();
    }

    public static DuelKit uhc() {
        return new Builder()
                .armor(armorPiece(Material.DIAMOND_HELMET, 2), armorPiece(Material.DIAMOND_CHESTPLATE, 2),
                        armorPiece(Material.DIAMOND_LEGGINGS, 2), armorPiece(Material.DIAMOND_BOOTS, 2))
                .set(0, sword(Material.DIAMOND_SWORD, 3, false))
                .set(1, bow(2, 0))
                .set(2, item(Material.ARROW, 24))
                .set(3, gapples(8))
                .set(4, potion(PotionType.INSTANT_HEAL, 2))
                .build();
    }

    public static DuelKit diamond() {
        return new Builder()
                .armor(item(Material.DIAMOND_HELMET), item(Material.DIAMOND_CHESTPLATE),
                        item(Material.DIAMOND_LEGGINGS), item(Material.DIAMOND_BOOTS))
                .set(0, sword(Material.DIAMOND_SWORD, 2, false))
                .set(1, gapples(16))
                .build();
    }

    public static DuelKit iron() {
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 1), armorPiece(Material.IRON_CHESTPLATE, 1),
                        armorPiece(Material.IRON_LEGGINGS, 1), armorPiece(Material.IRON_BOOTS, 1))
                .set(0, sword(Material.IRON_SWORD, 2, false))
                .set(1, gapples(8))
                .build();
    }

    public static DuelKit gold() {
        return new Builder()
                .armor(armorPiece(Material.GOLDEN_HELMET, 1), armorPiece(Material.GOLDEN_CHESTPLATE, 1),
                        armorPiece(Material.GOLDEN_LEGGINGS, 1), armorPiece(Material.GOLDEN_BOOTS, 1))
                .set(0, sword(Material.GOLDEN_SWORD, 5, false))
                .set(1, gapples(16))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit netherite() {
        return new Builder()
                .armor(armorPiece(Material.NETHERITE_HELMET, 3), armorPiece(Material.NETHERITE_CHESTPLATE, 3),
                        armorPiece(Material.NETHERITE_LEGGINGS, 3), armorPiece(Material.NETHERITE_BOOTS, 3))
                .set(0, sword(Material.NETHERITE_SWORD, 3, true))
                .set(1, gapples(8))
                .set(2, potion(PotionType.INSTANT_HEAL, 2))
                .effect(DuelKit.infinite(PotionEffectType.FIRE_RESISTANCE, 0))
                .build();
    }

    public static DuelKit axe() {
        ItemStack shield = named(item(Material.SHIELD), "<white>盾牌");
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 2), armorPiece(Material.IRON_CHESTPLATE, 2),
                        armorPiece(Material.IRON_LEGGINGS, 2), armorPiece(Material.IRON_BOOTS, 2))
                .set(0, sword(Material.IRON_AXE, 2, false))
                .set(1, gapples(4))
                .offhand(shield)
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit trident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ench(trident, Enchantment.LOYALTY, 3);
        ench(trident, Enchantment.IMPALING, 5);
        ench(trident, Enchantment.CHANNELING, 1);
        ench(trident, Enchantment.DURABILITY, 3);
        return new Builder()
                .armor(armorPiece(Material.DIAMOND_HELMET, 2), armorPiece(Material.DIAMOND_CHESTPLATE, 2),
                        armorPiece(Material.DIAMOND_LEGGINGS, 2), armorPiece(Material.DIAMOND_BOOTS, 2))
                .set(0, trident)
                .set(1, item(Material.ENDER_PEARL, 4))
                .set(2, gapples(4))
                .build();
    }

    public static DuelKit crystal() {
        return new Builder()
                .armor(armorPieceBlast(Material.NETHERITE_HELMET, 4), armorPieceBlast(Material.NETHERITE_CHESTPLATE, 4),
                        armorPieceBlast(Material.NETHERITE_LEGGINGS, 4), armorPieceBlast(Material.NETHERITE_BOOTS, 4))
                .set(0, sword(Material.NETHERITE_SWORD, 3, false))
                .set(1, item(Material.END_CRYSTAL, 64))
                .set(2, item(Material.END_CRYSTAL, 64))
                .set(3, item(Material.OBSIDIAN, 64))
                .set(4, item(Material.OBSIDIAN, 64))
                .set(5, item(Material.TOTEM_OF_UNDYING))
                .set(6, item(Material.TOTEM_OF_UNDYING))
                .set(7, gapples(16))
                .effect(DuelKit.infinite(PotionEffectType.FIRE_RESISTANCE, 0))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 0))
                .build();
    }

    public static DuelKit oneHit() {
        return new Builder()
                .armor(null, null, null, null)
                .set(0, sword(Material.WOODEN_SWORD, 5, false))
                .build();
    }

    public static DuelKit combo() {
        ItemStack stick = new ItemStack(Material.STICK);
        ench(stick, Enchantment.DAMAGE_ALL, 1);
        ench(stick, Enchantment.KNOCKBACK, 2);
        return new Builder()
                .armor(null, null, null, null)
                .set(0, named(stick, "<yellow>连击棒 <gray>(命中把对手挑飞)"))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

    public static DuelKit pearl() {
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 1), armorPiece(Material.IRON_CHESTPLATE, 1),
                        armorPiece(Material.IRON_LEGGINGS, 1), armorPiece(Material.IRON_BOOTS, 1))
                .set(0, sword(Material.IRON_SWORD, 2, false))
                .set(1, item(Material.ENDER_PEARL, 16))
                .set(2, gapples(8))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 0))
                .build();
    }

    public static DuelKit fireball() {
        return new Builder()
                .armor(armorPiece(Material.IRON_HELMET, 2), armorPiece(Material.IRON_CHESTPLATE, 2),
                        armorPiece(Material.IRON_LEGGINGS, 2), armorPiece(Material.IRON_BOOTS, 2))
                .set(0, sword(Material.IRON_SWORD, 2, false))
                .set(1, named(item(Material.FIRE_CHARGE, 16), "<red>火焰弹 <gray>(右键发射)"))
                .set(2, gapples(8))
                .effect(DuelKit.infinite(PotionEffectType.SPEED, 1))
                .build();
    }

}
