package com.teslamaps.features.qol;

// Skytils CalcXPCommand: /calcxp <type> <start> <end>
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkillXp;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class CalcXp {

    private static final long[] CATA = {
            0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
            4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
            135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640,
            3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640,
            51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640,
            569809640
    };
    private static final long[] ZOMBIE = {0, 5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] SPIDER = {0, 5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] SLAYER = {0, 10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] VAMPIRE = {0, 20, 75, 240, 840, 2400};

    private static final List<String> TYPES = List.of("skill", "dungeons", "zombie_slayer", "spider_slayer",
            "wolf_slayer", "enderman_slayer", "blaze_slayer", "vampire_slayer");

    private CalcXp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CalcXp").config("Skills", b -> b.toggle("/calcxp Command", () -> cfg().calcXpCommand, v -> cfg().calcXpCommand = v)
                .desc("/calcxp <skill|dungeons|<slayer>_slayer> <start> <end> prints the xp between two levels. Decimals work."));
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("calcxp")
                .then(ClientCommands.argument("type", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                            for (String t : TYPES) if (t.startsWith(typed) && !t.equals(typed)) sb.suggest(t);
                            return sb.buildFuture();
                        })
                        .then(ClientCommands.argument("start", DoubleArgumentType.doubleArg(0))
                                .then(ClientCommands.argument("end", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "type"),
                                                DoubleArgumentType.getDouble(c, "start"), DoubleArgumentType.getDouble(c, "end"))))))));
    }

    private static int run(FabricClientCommandSource src, String type, double start, double end) {
        TeslaMapsConfig c = cfg();
        if (!c.calcXpCommand || !c.section("Skills")) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] /calcxp is off. Turn it on under Skills > /calcxp Command."));
            return 0;
        }
        type = type.toLowerCase(Locale.ROOT);
        double max = maxLevel(type);
        if (max < 0) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] Unknown type §f" + type + "§c. Use one of: " + String.join(", ", TYPES)));
            return 0;
        }
        start = Math.min(start, max);
        end = Math.min(end, max);
        if (end < start) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] The start level must be lower than the end level."));
            return 0;
        }
        long need = Math.round(total(type, end) - total(type, start));
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fYou need §6" + String.format(Locale.ROOT, "%,d", need)
                + " xp §fto get §b" + type + " §ffrom level §6" + fmt(start) + " §fto §6" + fmt(end) + "§f."));
        return 1;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? Integer.toString((int) d) : Double.toString(d);
    }

    private static double maxLevel(String type) {
        return switch (type) {
            case "skill" -> 100;
            case "dungeons", "catacombs", "cata" -> 100;
            case "vampire_slayer" -> VAMPIRE.length - 1;
            case "zombie_slayer", "spider_slayer", "wolf_slayer", "enderman_slayer", "blaze_slayer" -> ZOMBIE.length - 1;
            default -> -1;
        };
    }

    private static double total(String type, double level) {
        int lo = (int) Math.floor(level);
        double frac = level - lo;
        double a = at(type, lo);
        return frac == 0 ? a : a + frac * (at(type, lo + 1) - a);
    }

    private static double at(String type, int level) {
        return switch (type) {
            case "skill" -> SkillXp.xpRequiredForLevel(level);
            case "dungeons", "catacombs", "cata" -> level <= 50 ? CATA[level] : CATA[50] + (level - 50) * 200_000_000.0;
            case "zombie_slayer" -> table(ZOMBIE, level);
            case "spider_slayer" -> table(SPIDER, level);
            case "vampire_slayer" -> table(VAMPIRE, level);
            default -> table(SLAYER, level);
        };
    }

    private static double table(long[] t, int level) {
        return t[Math.min(level, t.length - 1)];
    }
}
