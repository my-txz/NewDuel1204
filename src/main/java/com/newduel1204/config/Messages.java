package com.newduel1204.config;

import com.newduel1204.NewDuel1204;
import com.newduel1204.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 语言文件封装。支持占位符 {@code %key%}，MiniMessage 颜色。
 * 加载时会把 jar 内新增的默认键合并进用户文件，保证版本升级不丢配置（向后兼容）。
 */
public final class Messages {

    private final NewDuel1204 plugin;
    private FileConfiguration cfg = new YamlConfiguration();
    private String prefixRaw = "";

    public Messages(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
                cfg.options().copyDefaults(true);
                cfg.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法合并 messages.yml 默认值: " + e.getMessage());
        }
        prefixRaw = cfg.getString("prefix", "");
    }

    public String raw(String key, String... placeholders) {
        String value = cfg.getString(key);
        if (value == null) {
            value = "<red>[missing message: " + key + "]";
        }
        return apply(value, placeholders);
    }

    public List<String> rawList(String key, String... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(key)) {
            out.add(apply(line, placeholders));
        }
        return out;
    }

    public Component get(String key, String... placeholders) {
        return Text.mm(raw(key, placeholders));
    }

    public Component prefixed(String key, String... placeholders) {
        return Text.mm(prefixRaw).append(Text.mm(raw(key, placeholders)));
    }

    public void send(CommandSender target, String key, String... placeholders) {
        if (target == null) {
            return;
        }
        target.sendMessage(prefixed(key, placeholders));
    }

    public void sendList(CommandSender target, String key, String... placeholders) {
        if (target == null) {
            return;
        }
        List<String> lines = rawList(key, placeholders);
        for (int i = 0; i < lines.size(); i++) {
            Component line = Text.mm(lines.get(i));
            target.sendMessage(i == 0 ? Text.mm(prefixRaw).append(line) : line);
        }
    }

    public void broadcast(String key, String... placeholders) {
        Component msg = prefixed(key, placeholders);
        Bukkit.getServer().sendMessage(msg);
    }

    public Component prefix() {
        return Text.mm(prefixRaw);
    }

    private static String apply(String input, String... placeholders) {
        if (input == null) {
            return "";
        }
        String out = input;
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                String key = placeholders[i];
                String value = placeholders[i + 1] == null ? "" : placeholders[i + 1];
                out = out.replace("%" + key + "%", value);
            }
        }
        return out;
    }
}
