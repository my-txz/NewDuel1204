package com.newduel1204.kit;

import com.newduel1204.arena.ArenaKind;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 全部对战模式。新增模式只需在这里加一行 + 在 {@link Kits} 里写一套装备。
 */
public enum GameMode {

    NODEBUFF("nodebuff", "无药水决斗", Material.DIAMOND_SWORD, ArenaKind.FLAT, Kits::noDebuff,
            "经典的 1v1 纯近战模式", "钻石套保护IV · 锋利V剑 · 永久速度II", "只有治疗药水，没有负面药水"),

    DEBUFF("debuff", "药水决斗", Material.SPLASH_POTION, ArenaKind.FLAT, Kits::debuff,
            "带负面药水的完整药水战", "毒药 · 迟缓 · 伤害药水齐上阵", "走位与药水节奏的博弈"),

    CLASSIC("classic", "经典决斗", Material.IRON_SWORD, ArenaKind.FLAT, Kits::classic,
            "铁装 + 弓的复古决斗", "适合新手的入门模式"),

    GAPPLE("gapple", "金苹果决斗", Material.GOLDEN_APPLE, ArenaKind.FLAT, Kits::gapple,
            "满背包金苹果的消耗战", "32 个金苹果 · 保护II钻石套", "比拼续航与换血"),

    SOUP("soup", "蘑菇汤决斗", Material.MUSHROOM_STEW, ArenaKind.FLAT, Kits::soup,
            "喝汤回血的经典玩法", "右键蘑菇汤立刻回复 3.5 颗心", "手速与节奏的极致考验"),

    SUMO("sumo", "相扑", Material.SLIME_BALL, ArenaKind.RING, Kits::sumo,
            "高空圆台 · 空手对推", "不能造成伤害，把对手推下平台即胜", "击退、走位、边缘控制"),

    BOXING("boxing", "拳击", Material.LEATHER_CHESTPLATE, ArenaKind.FLAT, Kits::boxing,
            "赤手空拳比命中数", "先命中 100 次者获胜", "没有伤害，只有连击"),

    ARCHER("archer", "弓箭决斗", Material.BOW, ArenaKind.FLAT, Kits::archer,
            "远程对射", "力量III 弓 · 皮革套", "预判与走位"),

    BUILDUHC("builduhc", "建筑 UHC", Material.OAK_PLANKS, ArenaKind.FLAT, Kits::buildUhc,
            "可以搭桥、放水、放岩浆的 UHC", "无自然回血 · 依靠金苹果续航", "地形就是你的武器"),

    UHC("uhc", "极限生存决斗", Material.GOLDEN_APPLE, ArenaKind.FLAT, Kits::uhc,
            "无自然回血的高压对决", "保护II钻石套 · 8 个金苹果", "每一次交战都可能是最后一次"),

    DIAMOND("diamond", "钻石装", Material.DIAMOND_CHESTPLATE, ArenaKind.FLAT, Kits::diamond,
            "最纯粹的装备对拼", "无附魔钻石套 · 锋利II剑 · 16 金苹果"),

    IRON("iron", "铁装", Material.IRON_CHESTPLATE, ArenaKind.FLAT, Kits::iron,
            "轻量级装备对拼", "铁套保护I · 8 金苹果"),

    GOLD("gold", "金装", Material.GOLDEN_CHESTPLATE, ArenaKind.FLAT, Kits::gold,
            "脆皮高速的刺客对局", "金装 · 锋利V金剑 · 速度II", "两三下就能分胜负"),

    NETHERITE("netherite", "下界合金", Material.NETHERITE_SWORD, ArenaKind.FLAT, Kits::netherite,
            "重装 + 火焰附加的硬碰硬", "保护III合金套 · 火焰附加剑 · 抗火"),

    AXE("axe", "盾斧决斗", Material.IRON_AXE, ArenaKind.FLAT, Kits::axe,
            "斧 + 盾的破防博弈", "破盾、格挡、绕后"),

    TRIDENT("trident", "三叉戟", Material.TRIDENT, ArenaKind.FLAT, Kits::trident,
            "投掷与近战结合的模式", "忠诚III · 穿刺V · 引雷", "还有末影珍珠可以追击"),

    CRYSTAL("crystal", "水晶 PvP", Material.END_CRYSTAL, ArenaKind.CRYSTAL, Kits::crystal,
            "放水晶、炸水晶的高端玩法", "128 个末影水晶 · 64 黑曜石 · 2 个不死图腾", "自伤与爆发的极限计算"),

    ONEHIT("onehit", "一击必杀", Material.WOODEN_SWORD, ArenaKind.FLAT, Kits::oneHit,
            "谁先碰到谁就赢", "木剑 · 无护甲 · 一次命中即结束"),

    COMBO("combo", "连击", Material.STICK, ArenaKind.FLAT, Kits::combo,
            "把对手挑到天上连击", "锋利I 击退II 木棍 · 无护甲"),

    PEARL("pearl", "珍珠战", Material.ENDER_PEARL, ArenaKind.FLAT, Kits::pearl,
            "16 颗末影珍珠的机动战", "铁装 · 锋利II剑 · 靠传送拉开距离"),

    FIREBALL("fireball", "火焰弹", Material.FIRE_CHARGE, ArenaKind.FLAT, Kits::fireball,
            "右键发射火焰弹轰炸对手", "16 发火焰弹 · 爆炸击退", "把对手炸进虚空");

    private final ModeSpec spec;

    GameMode(String id, String display, Material icon, ArenaKind arena, java.util.function.Supplier<DuelKit> kit,
             String... description) {
        this.spec = ModeSpec.of(id, display, icon, arena, kit).desc(description);
    }

    /* 各模式的特殊规则（在静态块里配置，保持枚举常量行简短易读） */
    static {
        SUMO.spec.combat(CombatRule.NO_DAMAGE).win(WinCondition.RING_OUT).timeLimit(120);
        BOXING.spec.combat(CombatRule.NO_DAMAGE).win(WinCondition.HITS).hitTarget(100).timeLimit(240);
        ONEHIT.spec.combat(CombatRule.ONE_HIT).timeLimit(180);
        COMBO.spec.comboLaunch().timeLimit(240);
        SOUP.spec.soupHeal().timeLimit(300);
        FIREBALL.spec.fireball().timeLimit(240);
        ARCHER.spec.timeLimit(300);
        BUILDUHC.spec.build(true).noRegen().timeLimit(480);
        CRYSTAL.spec.build(true).timeLimit(420);
        UHC.spec.noRegen();
        NETHERITE.spec.noRegen();
        // 其余模式使用默认：正常伤害、击杀获胜、自然回血、默认时长
    }

    public ModeSpec spec() {
        return spec;
    }

    public String id() {
        return spec.id;
    }

    public String display() {
        return spec.display;
    }

    public Material icon() {
        return spec.icon;
    }

    public ArenaKind arena() {
        return spec.arena;
    }

    public List<String> description() {
        return spec.description;
    }

    public DuelKit kit() {
        return spec.kit.get();
    }

    public CombatRule combat() {
        return spec.combat;
    }

    public WinCondition win() {
        return spec.win;
    }

    public boolean naturalRegen() {
        return spec.naturalRegen;
    }

    public int timeLimit() {
        return spec.timeLimit;
    }

    public int hitTarget() {
        return spec.hitTarget;
    }

    public boolean allowBuild() {
        return spec.allowBuild;
    }

    public boolean allowBreak() {
        return spec.allowBreak;
    }

    public boolean soupHeal() {
        return spec.soupHeal;
    }

    public boolean fireball() {
        return spec.fireball;
    }

    public boolean comboLaunch() {
        return spec.comboLaunch;
    }

    public String typeLabel() {
        return switch (spec.combat) {
            case NO_DAMAGE -> "<yellow>技巧";
            case ONE_HIT -> "<red>一击";
            case NORMAL -> "<aqua>对战";
        };
    }

    public static Optional<GameMode> byId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = input.toLowerCase(Locale.ROOT).trim();
        for (GameMode mode : values()) {
            if (mode.id().equals(key)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** 模糊匹配：允许输入中文名或模式 id 的一部分。 */
    public static List<GameMode> search(String input) {
        String key = input.toLowerCase(Locale.ROOT).trim();
        List<GameMode> result = new ArrayList<>();
        for (GameMode mode : values()) {
            if (mode.id().contains(key) || mode.display().contains(key)) {
                result.add(mode);
            }
        }
        return result;
    }

    public static GameMode random() {
        GameMode[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    public static List<String> ids() {
        List<String> list = new ArrayList<>();
        for (GameMode mode : values()) {
            list.add(mode.id());
        }
        return list;
    }
}
