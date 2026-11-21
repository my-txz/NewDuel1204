package com.newduel1204.player;

/**
 * 段位与 ELO 计算。
 */
public final class Ranking {

    private static final int[] THRESHOLDS = {0, 900, 1050, 1200, 1350, 1500, 1650};
    private static final String[] NAMES = {"木质", "铁质", "金质", "铂金", "钻石", "大师", "宗师"};
    private static final String[] COLORS = {
            "<dark_gray>", "<gray>", "<gold>", "<aqua>", "<light_purple>", "<red>", "<gradient:#ff2d55:#ffd60a>"
    };

    private Ranking() {
    }

    public static int tier(int elo) {
        int tier = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (elo >= THRESHOLDS[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /** 带颜色 + 小段位（如 钻石 II）。 */
    public static String division(int elo) {
        int tier = tier(elo);
        int base = THRESHOLDS[tier];
        int next = tier + 1 < THRESHOLDS.length ? THRESHOLDS[tier + 1] : base + 200;
        int span = Math.max(1, next - base);
        int sub = 1 + (int) Math.min(2, Math.floor((elo - base) * 3.0 / span));
        return COLORS[tier] + NAMES[tier] + " " + toRoman(sub) + "</" + tagOf(COLORS[tier]) + ">";
    }

    public static String plainDivision(int elo) {
        return NAMES[tier(elo)];
    }

    public static int nextThreshold(int elo) {
        int tier = tier(elo);
        return tier + 1 < THRESHOLDS.length ? THRESHOLDS[tier + 1] : THRESHOLDS[tier] + 200;
    }

    private static String toRoman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    private static String tagOf(String color) {
        if (color.startsWith("<gradient")) {
            return "gradient";
        }
        return color.substring(1, color.length() - 1);
    }

    /** 标准 ELO 期望胜率。 */
    public static double expected(double myElo, double opponentElo) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentElo - myElo) / 400.0));
    }

    /** 计算新分数。provisional（定级赛）阶段使用更大的 K 值。 */
    public static int newElo(int myElo, int opponentElo, boolean win, int kFactor, boolean provisional) {
        int k = provisional ? (int) Math.round(kFactor * 1.75) : kFactor;
        double score = win ? 1.0 : 0.0;
        double expected = expected(myElo, opponentElo);
        int delta = (int) Math.round(k * (score - expected));
        if (win && delta < 3) {
            delta = 3;
        }
        if (!win && delta > -3) {
            delta = -3;
        }
        return Math.max(0, myElo + delta);
    }

    /** 本局分数变化（用于展示 +12 / -8）。 */
    public static int delta(int myElo, int opponentElo, boolean win, int kFactor, boolean provisional) {
        return newElo(myElo, opponentElo, win, kFactor, provisional) - myElo;
    }
}
