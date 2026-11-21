package com.newduel1204.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * 轻量侧边栏 HUD：只为每一局创建一个记分板，两名玩家共用，
 * 更新时只改队伍前缀（不重建对象），刷新频率由配置控制。
 */
public final class Sidebar {

    private final Scoreboard board;
    private final Objective objective;
    private final Team[] teams;
    private final String[] entries;

    public Sidebar(Component title, int lines) {
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("newduel", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.teams = new Team[lines];
        this.entries = new String[lines];
        for (int i = 0; i < lines; i++) {
            String entry = "\u00a7" + Integer.toHexString(i);
            this.entries[i] = entry;
            Team team = board.registerNewTeam("nd" + i);
            team.addEntry(entry);
            teams[i] = team;
            objective.getScore(entry).setScore(lines - i);
        }
    }

    public void line(int index, Component text) {
        if (index < 0 || index >= teams.length) {
            return;
        }
        teams[index].prefix(text);
    }

    public void line(int index, String miniMessage) {
        line(index, com.newduel1204.util.Text.mm(miniMessage));
    }

    public void title(Component title) {
        objective.displayName(title);
    }

    public void show(Player player) {
        if (player != null && player.isOnline()) {
            player.setScoreboard(board);
        }
    }

    public void destroy() {
        for (Team team : teams) {
            try {
                team.unregister();
            } catch (Exception ignored) {
            }
        }
        try {
            objective.unregister();
        } catch (Exception ignored) {
        }
    }
}
