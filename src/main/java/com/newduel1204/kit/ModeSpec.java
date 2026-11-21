package com.newduel1204.kit;

import com.newduel1204.arena.ArenaKind;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 一个模式的完整定义（可读的流式构造，避免超长构造参数列表）。
 */
public final class ModeSpec {

    public final String id;
    public final String display;
    public final Material icon;
    public final ArenaKind arena;
    public final Supplier<DuelKit> kit;

    public List<String> description = new ArrayList<>();
    public boolean allowBuild = false;
    public boolean allowBreak = false;
    public CombatRule combat = CombatRule.NORMAL;
    public WinCondition win = WinCondition.KILL;
    public boolean naturalRegen = true;
    public int timeLimit = 0;
    public int hitTarget = 0;
    public boolean soupHeal = false;
    public boolean fireball = false;
    public boolean comboLaunch = false;

    private ModeSpec(String id, String display, Material icon, ArenaKind arena, Supplier<DuelKit> kit) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.arena = arena;
        this.kit = kit;
    }

    public static ModeSpec of(String id, String display, Material icon, ArenaKind arena, Supplier<DuelKit> kit) {
        return new ModeSpec(id, display, icon, arena, kit);
    }

    public ModeSpec desc(String... lines) {
        this.description = List.of(lines);
        return this;
    }

    public ModeSpec build(boolean build) {
        this.allowBuild = build;
        this.allowBreak = build;
        return this;
    }

    public ModeSpec combat(CombatRule rule) {
        this.combat = rule;
        return this;
    }

    public ModeSpec win(WinCondition condition) {
        this.win = condition;
        return this;
    }

    public ModeSpec noRegen() {
        this.naturalRegen = false;
        return this;
    }

    public ModeSpec timeLimit(int seconds) {
        this.timeLimit = seconds;
        return this;
    }

    public ModeSpec hitTarget(int hits) {
        this.hitTarget = hits;
        return this;
    }

    public ModeSpec soupHeal() {
        this.soupHeal = true;
        return this;
    }

    public ModeSpec fireball() {
        this.fireball = true;
        return this;
    }

    public ModeSpec comboLaunch() {
        this.comboLaunch = true;
        return this;
    }
}
