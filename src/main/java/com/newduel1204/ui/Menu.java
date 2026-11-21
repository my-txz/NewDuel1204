package com.newduel1204.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 极简 GUI 框架：一个菜单 = 一个 Inventory + 槽位回调表。
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private final String id;

    public Menu(String id, int rows, Component title) {
        this.id = id;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public Menu set(int slot, ItemStack item, Consumer<Player> action) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
            if (action != null) {
                actions.put(slot, action);
            }
        }
        return this;
    }

    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    public void fill(ItemStack item) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void click(Player player, int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    public String id() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
