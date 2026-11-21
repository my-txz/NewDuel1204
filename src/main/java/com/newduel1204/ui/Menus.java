package com.newduel1204.ui;

import com.newduel1204.NewDuel1204;
import com.newduel1204.kit.GameMode;
import com.newduel1204.kit.Kits;
import com.newduel1204.match.DuelMatch;
import com.newduel1204.player.PlayerProfile;
import com.newduel1204.player.Ranking;
import com.newduel1204.util.Items;
import com.newduel1204.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 全部 GUI 界面。
 */
public final class Menus {

    public enum Purpose {
        INVITE, QUEUE_NORMAL, QUEUE_RANKED
    }

    private final NewDuel1204 plugin;

    public Menus(NewDuel1204 plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------- 主菜单 */

    public void openMain(Player player) {
        Menu menu = new Menu("main", 5, Text.mm("<dark_gray>决斗中心"));
        ItemStack filler = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        PlayerProfile profile = plugin.profiles().get(player);
        menu.set(4, Items.head(player)
                .name("<gradient:#5ee7ff:#a06bff><bold>" + player.getName() + "</bold></gradient>")
                .lore("<gray>段位: " + Ranking.division(profile.elo),
                        "<gray>排位分: <white>" + profile.elo,
                        "<gray>战绩: <green>" + profile.wins + "<gray>胜 <red>" + profile.losses + "<gray>负",
                        "<gray>胜率: <white>" + String.format("%.1f%%", profile.winRate()),
                        "<dark_gray>排名: #" + plugin.profiles().rankOf(player.getUniqueId(), null))
                .build());

        menu.set(20, Items.of(Material.IRON_SWORD).name("<green><bold>邀请对战</bold>")
                .lore("<gray>选择一名在线玩家发出挑战", "<dark_gray>对方接受后立即开始").glow(true).build(),
                p -> openPlayerSelect(p));

        menu.set(22, Items.of(Material.COMPASS).name("<aqua><bold>匹配对战</bold>")
                .lore("<gray>自动匹配实力相近的对手", "<dark_gray>不影响排位分").build(),
                p -> openModeSelect(p, Purpose.QUEUE_NORMAL, null));

        menu.set(24, Items.of(Material.NETHER_STAR).name("<light_purple><bold>排位赛</bold>")
                .lore("<gray>计入 ELO 排位分", "<dark_gray>当前赛季: <white>" + plugin.settings().season()).build(),
                p -> {
                    if (!plugin.settings().rankedEnabled()) {
                        plugin.messages().send(p, "queue.ranked-disabled");
                        return;
                    }
                    openModeSelect(p, Purpose.QUEUE_RANKED, null);
                });

        menu.set(29, Items.of(Material.WRITABLE_BOOK).name("<yellow><bold>我的战绩</bold>")
                .lore("<gray>查看详细的生涯数据").build(),
                p -> openStats(p, p));

        menu.set(31, Items.of(Material.GOLD_INGOT).name("<gold><bold>排行榜</bold>")
                .lore("<gray>查看服务器顶尖玩家").build(),
                p -> openTop(p, null));

        menu.set(33, Items.of(Material.ENDER_EYE).name("<blue><bold>观战</bold>")
                .lore("<gray>观看正在进行的对局").build(),
                p -> openSpectate(p));

        menu.set(40, Items.of(Material.BARRIER).name("<red>关闭").build(), Player::closeInventory);
        menu.open(player);
    }

    /* ----------------------------------------------------------------- 选择模式 */

    public void openModeSelect(Player player, Purpose purpose, Player target) {
        String title = switch (purpose) {
            case INVITE -> "<dark_gray>选择模式 · 挑战 " + target.getName();
            case QUEUE_NORMAL -> "<dark_gray>选择模式 · 匹配";
            case QUEUE_RANKED -> "<dark_gray>选择模式 · 排位";
        };
        Menu menu = new Menu("modes", 5, Text.mm(title));
        ItemStack filler = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        GameMode[] modes = GameMode.values();
        int[] slots = buildSlots(9, 36, modes.length);
        for (int i = 0; i < modes.length && i < slots.length; i++) {
            GameMode mode = modes[i];
            menu.set(slots[i], modeIcon(mode, player), p -> {
                p.closeInventory();
                if (purpose == Purpose.INVITE && target != null) {
                    invite(p, target, mode);
                } else {
                    plugin.queues().join(p, mode, purpose == Purpose.QUEUE_RANKED);
                }
            });
        }

        menu.set(40, Items.of(Material.NETHER_STAR).name("<yellow><bold>随机模式</bold>")
                .lore("<gray>交给命运决定玩什么").build(), p -> {
            p.closeInventory();
            GameMode random = GameMode.random();
            if (purpose == Purpose.INVITE && target != null) {
                invite(p, target, random);
            } else {
                plugin.queues().join(p, random, purpose == Purpose.QUEUE_RANKED);
            }
        });
        menu.set(36, Items.of(Material.ARROW).name("<gray>返回").build(), this::openMain);
        menu.open(player);
    }

    private int[] buildSlots(int from, int count, int needed) {
        List<Integer> list = new ArrayList<>();
        for (int slot = from; slot < from + count && list.size() < needed; slot++) {
            int column = slot % 9;
            if (column == 0 || column == 8) {
                continue;
            }
            list.add(slot);
        }
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private ItemStack modeIcon(GameMode mode, Player viewer) {
        PlayerProfile profile = plugin.profiles().get(viewer);
        List<String> lore = new ArrayList<>();
        for (String line : mode.description()) {
            lore.add("<gray>" + line);
        }
        lore.add(" ");
        lore.add("<dark_gray>类型: " + mode.typeLabel());
        lore.add("<dark_gray>场地: <white>" + mode.arena().displayName());
        lore.add("<dark_gray>时长: <white>" + Text.formatTime(mode.timeLimit() > 0
                ? mode.timeLimit() : plugin.settings().defaultTimeLimit()));
        lore.add("<dark_gray>你的排位分: <white>" + profile.modeElo(mode.id(), plugin.settings().startElo())
                + " <dark_gray>(" + Ranking.plainDivision(profile.modeElo(mode.id(), plugin.settings().startElo())) + ")");
        lore.add(" ");
        lore.add("<yellow>点击选择");
        return Items.of(mode.icon()).name("<white><bold>" + mode.display() + "</bold>")
                .lore(lore).glow(false).build();
    }

    private void invite(Player sender, Player target, GameMode mode) {
        plugin.requests().send(sender, target, mode);
        plugin.messages().send(sender, "duel.invite-sent", "player", target.getName(), "mode", mode.display());
        plugin.messages().send(target, "duel.invite-received", "player", sender.getName(), "mode", mode.display());
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    /* ----------------------------------------------------------------- 选择玩家 */

    public void openPlayerSelect(Player player) {
        Menu menu = new Menu("players", 5, Text.mm("<dark_gray>选择对手"));
        ItemStack filler = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !plugin.matches().isBusy(online)) {
                targets.add(online);
            }
        }
        int[] slots = buildSlots(0, 36, Math.max(1, targets.size()));
        for (int i = 0; i < targets.size() && i < slots.length; i++) {
            Player target = targets.get(i);
            PlayerProfile profile = plugin.profiles().get(target);
            menu.set(slots[i], Items.head(target)
                    .name("<white>" + target.getName())
                    .lore("<gray>段位: " + Ranking.division(profile.elo),
                            "<gray>战绩: <green>" + profile.wins + " <gray>胜 <red>" + profile.losses + " <gray>负",
                            " ",
                            "<yellow>点击选择模式")
                    .build(),
                    p -> openModeSelect(p, Purpose.INVITE, target));
        }
        if (targets.isEmpty()) {
            menu.set(22, Items.of(Material.BARRIER).name("<red>没有可挑战的玩家")
                    .lore("<gray>其他玩家可能正在对局中").build());
        }
        menu.set(36, Items.of(Material.ARROW).name("<gray>返回").build(), this::openMain);
        menu.open(player);
    }

    /* ------------------------------------------------------------------- 战绩 */

    public void openStats(Player viewer, OfflinePlayer target) {
        PlayerProfile profile = plugin.profiles().get(target.getUniqueId());
        Menu menu = new Menu("stats", 6, Text.mm("<dark_gray>" + target.getName() + " 的战绩"));
        ItemStack filler = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        menu.set(4, Items.head(target)
                .name("<white><bold>" + (profile.name.isEmpty() ? target.getName() : profile.name) + "</bold>")
                .lore("<gray>段位: " + Ranking.division(profile.elo),
                        "<gray>排位分: <white>" + profile.elo,
                        "<gray>总排名: <white>#" + plugin.profiles().rankOf(target.getUniqueId(), null),
                        "<gray>赛季: <white>" + profile.season)
                .lore("<gray>胜场: <green>" + profile.wins,
                        "<gray>负场: <red>" + profile.losses,
                        "<gray>胜率: <white>" + String.format("%.1f%%", profile.winRate()),
                        "<gray>击杀: <white>" + profile.kills + " <gray>死亡: <white>" + profile.deaths,
                        "<gray>当前连胜: <white>" + profile.streak + " <gray>最高连胜: <white>" + profile.bestStreak)
                .build());

        int slot = 19;
        for (GameMode mode : GameMode.values()) {
            if (slot >= 44) {
                break;
            }
            int wins = profile.modeWins(mode.id());
            int losses = profile.modeLosses(mode.id());
            if (wins + losses == 0) {
                continue;
            }
            int elo = profile.modeElo(mode.id(), plugin.settings().startElo());
            menu.set(slot++, Items.of(mode.icon())
                    .name("<white>" + mode.display())
                    .lore("<gray>段位: " + Ranking.division(elo),
                            "<gray>分数: <white>" + elo,
                            "<gray>战绩: <green>" + wins + " <gray>/ <red>" + losses)
                    .build());
        }

        List<String> history = new ArrayList<>();
        history.add("<gray>最近对局");
        if (profile.history.isEmpty()) {
            history.add("<dark_gray>暂无记录");
        } else {
            for (String line : profile.history) {
                String[] parts = line.split("\\|");
                if (parts.length < 4) {
                    continue;
                }
                String color = parts[3].startsWith("胜") ? "<green>" : "<red>";
                history.add("<dark_gray>" + parts[0] + " " + color + parts[3]
                        + " <gray>" + parts[1] + " <dark_gray>vs <white>" + parts[2]);
            }
        }
        menu.set(49, Items.of(Material.BOOK).name("<yellow>对战记录").lore(history).build());
        menu.set(45, Items.of(Material.ARROW).name("<gray>返回").build(), this::openMain);
        menu.open(viewer);
    }

    /* ---------------------------------------------------------------- 排行榜 */

    public void openTop(Player viewer, String mode) {
        String label = mode == null || mode.equalsIgnoreCase("global") ? "总榜"
                : GameMode.byId(mode).map(GameMode::display).orElse("总榜");
        Menu menu = new Menu("top", 6, Text.mm("<dark_gray>排行榜 · " + label));
        ItemStack filler = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        List<PlayerProfile.Standing> top = plugin.profiles().top(mode, 10);
        int[] slots = {13, 20, 21, 22, 23, 24, 29, 30, 31, 32};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            PlayerProfile.Standing standing = top.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(standing.uuid());
            menu.set(slots[i], Items.head(owner)
                    .name("<white><bold>#" + (i + 1) + " " + (standing.name().isEmpty()
                            ? owner.getName() : standing.name()) + "</bold>")
                    .lore("<gray>段位: " + Ranking.division(standing.elo()),
                            "<gray>分数: <white>" + standing.elo(),
                            "<gray>战绩: <green>" + standing.wins() + " <gray>/ <red>" + standing.losses())
                    .build());
        }
        if (top.isEmpty()) {
            menu.set(22, Items.of(Material.BARRIER).name("<red>暂无排行数据").build());
        }

        String[] quickModes = {"global", "nodebuff", "sumo", "crystal", "gapple", "builduhc"};
        String current = mode == null || mode.isBlank() ? "global" : mode;
        int slot = 46;
        for (String id : quickModes) {
            String name = id.equals("global") ? "总榜"
                    : GameMode.byId(id).map(GameMode::display).orElse(id);
            menu.set(slot++, Items.of(id.equals(current) ? Material.GOLD_BLOCK : Material.PAPER)
                    .name("<yellow>" + name)
                    .lore("<gray>点击切换").build(),
                    p -> openTop(p, id));
        }
        menu.set(45, Items.of(Material.ARROW).name("<gray>返回").build(), this::openMain);
        menu.open(viewer);
    }

    /* ------------------------------------------------------------------- 观战 */

    public void openSpectate(Player viewer) {
        Menu menu = new Menu("spectate", 5, Text.mm("<dark_gray>观战"));
        ItemStack filler = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        menu.fill(filler);

        List<DuelMatch> matches = new ArrayList<>(plugin.matches().active());
        int[] slots = buildSlots(0, 36, Math.max(1, matches.size()));
        for (int i = 0; i < matches.size() && i < slots.length; i++) {
            DuelMatch match = matches.get(i);
            Player a = Bukkit.getPlayer(match.players()[0]);
            Player b = Bukkit.getPlayer(match.players()[1]);
            String nameA = a == null ? "?" : a.getName();
            String nameB = b == null ? "?" : b.getName();
            menu.set(slots[i], Items.of(Material.ENDER_EYE)
                    .name("<white>" + nameA + " <gray>vs <white>" + nameB)
                    .lore("<gray>模式: <white>" + match.mode().display(),
                            "<gray>剩余: <white>" + match.remainingTime(),
                            "<gray>场地: <white>#" + match.arena().id(),
                            " ",
                            "<yellow>点击观战")
                    .build(), p -> {
                p.closeInventory();
                if (!p.hasPermission("newduel.spectate")) {
                    plugin.messages().send(p, "general.no-permission");
                    return;
                }
                if (a == null) {
                    plugin.messages().send(p, "duel.spectate-not-in-match");
                    return;
                }
                plugin.matches().addSpectator(p, match);
                plugin.messages().send(p, "duel.spectate-start", "player", nameA);
            });
        }
        if (matches.isEmpty()) {
            menu.set(22, Items.of(Material.BARRIER).name("<red>当前没有进行中的对局").build());
        }
        menu.set(36, Items.of(Material.ARROW).name("<gray>返回").build(), this::openMain);
        menu.open(viewer);
    }
}
