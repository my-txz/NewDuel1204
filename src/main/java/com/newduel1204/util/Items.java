package com.newduel1204.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品构造小工具，统一 GUI 与 kit 的写法。
 */
public final class Items {

    private Items() {
    }

    public static Builder of(Material material) {
        return new Builder(material, 1);
    }

    public static Builder of(Material material, int amount) {
        return new Builder(material, amount);
    }

    public static Builder head(OfflinePlayer owner) {
        Builder builder = new Builder(Material.PLAYER_HEAD, 1);
        builder.owner = owner;
        return builder;
    }

    public static final class Builder {
        private final Material material;
        private final int amount;
        private String name;
        private final List<String> lore = new ArrayList<>();
        private boolean glow;
        private OfflinePlayer owner;

        private Builder(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        public Builder name(String miniName) {
            this.name = miniName;
            return this;
        }

        public Builder lore(String... lines) {
            for (String line : lines) {
                this.lore.add(line);
            }
            return this;
        }

        public Builder lore(List<String> lines) {
            this.lore.addAll(lines);
            return this;
        }

        public Builder glow(boolean glow) {
            this.glow = glow;
            return this;
        }

        public ItemStack build() {
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (name != null) {
                    meta.displayName(Text.mm(name));
                }
                if (!lore.isEmpty()) {
                    List<Component> components = new ArrayList<>();
                    for (String line : lore) {
                        components.add(Text.mm(line));
                    }
                    meta.lore(components);
                }
                if (glow) {
                    meta.addEnchant(Enchantment.LURE, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (owner != null && meta instanceof SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(owner);
                }
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
