package com.newduel1204.player;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * ItemStack 数组的 Base64 编解码。
 * 使用 Bukkit 官方序列化流，能完整保留 NBT（附魔、耐久、自定义名称、药水内容、容器内容等）。
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化物品数据", e);
        }
    }

    public static ItemStack[] decode(String base64, int expectedSize) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack[expectedSize];
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            int size = in.readInt();
            ItemStack[] items = new ItemStack[Math.max(size, expectedSize)];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            if (items.length != expectedSize) {
                ItemStack[] resized = new ItemStack[expectedSize];
                System.arraycopy(items, 0, resized, 0, Math.min(items.length, expectedSize));
                return resized;
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("无法反序列化物品数据", e);
        }
    }
}
