package com.newduel1204.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 玩家完整状态快照。这是"数据安全"的核心结构：
 * 玩家进入竞技场之前把这里列出的所有状态完整保存下来，对局结束后逐字段还原。
 */
public final class PlayerSnapshot {

    /** 0-35 背包，36 靴子，37 护腿，38 胸甲，39 头盔，40 副手。 */
    public static final int TOTAL_SLOTS = 41;

    public UUID uuid;
    public String name;
    public long createdAt;

    public String worldName;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    public String inventory;
    public String enderChest;
    public int heldSlot;

    public double health;
    public double maxHealth;
    public int foodLevel;
    public float saturation;
    public float exhaustion;
    public int level;
    public float exp;
    public int totalExperience;

    public String gameMode;
    public boolean allowFlight;
    public boolean flying;
    public float walkSpeed;
    public float flySpeed;
    public boolean invulnerable;
    public int fireTicks;
    public int remainingAir;

    public List<String> effects = new ArrayList<>();
    public String state = "PENDING";

    public PlayerSnapshot() {
    }

    /* ---------------------------------------------------------------- capture */

    public static PlayerSnapshot capture(Player player) {
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.uuid = player.getUniqueId();
        snapshot.name = player.getName();
        snapshot.createdAt = System.currentTimeMillis();

        Location loc = player.getLocation();
        snapshot.worldName = loc.getWorld() == null ? null : loc.getWorld().getName();
        snapshot.x = loc.getX();
        snapshot.y = loc.getY();
        snapshot.z = loc.getZ();
        snapshot.yaw = loc.getYaw();
        snapshot.pitch = loc.getPitch();

        ItemStack[] combined = new ItemStack[TOTAL_SLOTS];
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < 36 && i < contents.length; i++) {
            combined[i] = contents[i];
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < 4 && i < armor.length; i++) {
            combined[36 + i] = armor[i];
        }
        combined[40] = player.getInventory().getItemInOffHand();
        snapshot.inventory = ItemCodec.encode(combined);
        snapshot.enderChest = ItemCodec.encode(player.getEnderChest().getContents());
        snapshot.heldSlot = player.getInventory().getHeldItemSlot();

        snapshot.health = player.getHealth();
        var maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        snapshot.maxHealth = maxHealth == null ? 20.0 : maxHealth.getBaseValue();
        snapshot.foodLevel = player.getFoodLevel();
        snapshot.saturation = player.getSaturation();
        snapshot.exhaustion = player.getExhaustion();
        snapshot.level = player.getLevel();
        snapshot.exp = player.getExp();
        snapshot.totalExperience = player.getTotalExperience();

        snapshot.gameMode = player.getGameMode().name();
        snapshot.allowFlight = player.getAllowFlight();
        snapshot.flying = player.isFlying();
        snapshot.walkSpeed = player.getWalkSpeed();
        snapshot.flySpeed = player.getFlySpeed();
        snapshot.invulnerable = player.isInvulnerable();
        snapshot.fireTicks = player.getFireTicks();
        snapshot.remainingAir = player.getRemainingAir();

        for (PotionEffect effect : player.getActivePotionEffects()) {
            snapshot.effects.add(effect.getType().getName() + ":" + effect.getDuration() + ":"
                    + effect.getAmplifier() + ":" + effect.isAmbient() + ":" + effect.hasParticles()
                    + ":" + effect.hasIcon());
        }
        return snapshot;
    }

    /* ---------------------------------------------------------------- restore */

    /**
     * 把快照写回玩家。逐段容错：任何一段失败都不会中断后续步骤，最大化数据可恢复性。
     *
     * @return 是否有异常发生（true 表示可能不完整，调用方应提示管理员）
     */
    public boolean apply(Player player) {
        boolean problem = false;
        try {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        } catch (Exception ignored) {
        }

        try {
            player.setFireTicks(0);
            player.setFallDistance(0f);
            player.setVelocity(player.getVelocity().zero());
            player.setRemainingAir(Math.max(remainingAir, 0));
        } catch (Exception ignored) {
        }

        try {
            GameMode mode = GameMode.valueOf(gameMode == null ? "SURVIVAL" : gameMode.toUpperCase(Locale.ROOT));
            player.setGameMode(mode);
        } catch (Exception e) {
            problem = true;
            try {
                player.setGameMode(GameMode.SURVIVAL);
            } catch (Exception ignored) {
            }
        }

        try {
            var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null && maxHealth > 0) {
                maxHealthAttr.setBaseValue(maxHealth);
            }
        } catch (Exception ignored) {
        }

        try {
            ItemStack[] combined = ItemCodec.decode(inventory, TOTAL_SLOTS);
            ItemStack[] contents = new ItemStack[36];
            System.arraycopy(combined, 0, contents, 0, 36);
            ItemStack[] armor = new ItemStack[4];
            System.arraycopy(combined, 36, armor, 0, 4);

            var inv = player.getInventory();
            inv.clear();
            inv.setContents(contents);
            inv.setArmorContents(armor);
            inv.setItemInOffHand(combined[40]);
            inv.setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
            player.updateInventory();
        } catch (Exception e) {
            problem = true;
        }

        try {
            ItemStack[] ender = ItemCodec.decode(enderChest, player.getEnderChest().getSize());
            player.getEnderChest().setContents(ender);
        } catch (Exception e) {
            problem = true;
        }

        try {
            double target = Math.max(0.5, Math.min(health, maxHealth > 0 ? maxHealth : 20.0));
            player.setHealth(target);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setLevel(level);
            player.setExp(Math.max(0f, Math.min(1f, exp)));
            player.setTotalExperience(Math.max(0, totalExperience));
        } catch (Exception e) {
            problem = true;
        }

        try {
            if (!effects.isEmpty()) {
                for (String encoded : effects) {
                    PotionEffect effect = parseEffect(encoded);
                    if (effect != null) {
                        player.addPotionEffect(effect);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            player.setWalkSpeed(Math.max(0f, Math.min(1f, walkSpeed)));
            player.setFlySpeed(Math.max(0f, Math.min(1f, flySpeed)));
            player.setAllowFlight(allowFlight);
            if (allowFlight && flying) {
                player.setFlying(true);
            }
            player.setInvulnerable(invulnerable);
        } catch (Exception ignored) {
        }

        try {
            Location target = location();
            if (target != null) {
                player.teleport(target);
            }
        } catch (Exception e) {
            problem = true;
        }
        return problem;
    }

    public Location location() {
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static PotionEffect parseEffect(String encoded) {
        String[] parts = encoded.split(":");
        if (parts.length < 3) {
            return null;
        }
        PotionEffectType type = PotionEffectType.getByName(parts[0]);
        if (type == null) {
            return null;
        }
        try {
            int duration = Integer.parseInt(parts[1]);
            int amplifier = Integer.parseInt(parts[2]);
            boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3]);
            boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4]);
            boolean icon = parts.length <= 5 || Boolean.parseBoolean(parts[5]);
            return new PotionEffect(type, duration, amplifier, ambient, particles, icon);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ------------------------------------------------------------ persistence */

    public void writeTo(ConfigurationSection section) {
        section.set("uuid", uuid.toString());
        section.set("name", name);
        section.set("created", createdAt);
        section.set("state", state);
        section.set("location.world", worldName);
        section.set("location.x", x);
        section.set("location.y", y);
        section.set("location.z", z);
        section.set("location.yaw", (double) yaw);
        section.set("location.pitch", (double) pitch);
        section.set("data.inventory", inventory);
        section.set("data.ender-chest", enderChest);
        section.set("data.held-slot", heldSlot);
        section.set("data.health", health);
        section.set("data.max-health", maxHealth);
        section.set("data.food", foodLevel);
        section.set("data.saturation", (double) saturation);
        section.set("data.exhaustion", (double) exhaustion);
        section.set("data.level", level);
        section.set("data.exp", (double) exp);
        section.set("data.total-experience", totalExperience);
        section.set("data.gamemode", gameMode);
        section.set("data.allow-flight", allowFlight);
        section.set("data.flying", flying);
        section.set("data.walk-speed", (double) walkSpeed);
        section.set("data.fly-speed", (double) flySpeed);
        section.set("data.invulnerable", invulnerable);
        section.set("data.fire-ticks", fireTicks);
        section.set("data.remaining-air", remainingAir);
        section.set("data.effects", effects);
    }

    public static PlayerSnapshot readFrom(ConfigurationSection section) {
        PlayerSnapshot s = new PlayerSnapshot();
        try {
            s.uuid = UUID.fromString(section.getString("uuid", ""));
        } catch (Exception e) {
            return null;
        }
        s.name = section.getString("name", "unknown");
        s.createdAt = section.getLong("created", 0L);
        s.state = section.getString("state", "PENDING");
        s.worldName = section.getString("location.world");
        s.x = section.getDouble("location.x");
        s.y = section.getDouble("location.y");
        s.z = section.getDouble("location.z");
        s.yaw = (float) section.getDouble("location.yaw");
        s.pitch = (float) section.getDouble("location.pitch");
        s.inventory = section.getString("data.inventory", "");
        s.enderChest = section.getString("data.ender-chest", "");
        s.heldSlot = section.getInt("data.held-slot", 0);
        s.health = section.getDouble("data.health", 20.0);
        s.maxHealth = section.getDouble("data.max-health", 20.0);
        s.foodLevel = section.getInt("data.food", 20);
        s.saturation = (float) section.getDouble("data.saturation", 5.0);
        s.exhaustion = (float) section.getDouble("data.exhaustion", 0.0);
        s.level = section.getInt("data.level", 0);
        s.exp = (float) section.getDouble("data.exp", 0.0);
        s.totalExperience = section.getInt("data.total-experience", 0);
        s.gameMode = section.getString("data.gamemode", "SURVIVAL");
        s.allowFlight = section.getBoolean("data.allow-flight", false);
        s.flying = section.getBoolean("data.flying", false);
        s.walkSpeed = (float) section.getDouble("data.walk-speed", 0.2);
        s.flySpeed = (float) section.getDouble("data.fly-speed", 0.1);
        s.invulnerable = section.getBoolean("data.invulnerable", false);
        s.fireTicks = section.getInt("data.fire-ticks", 0);
        s.remainingAir = section.getInt("data.remaining-air", 300);
        s.effects = new ArrayList<>(section.getStringList("data.effects"));
        return s;
    }
}
