package com.teslamaps.registry;

// GUI-021, after Skysoft's FeatureConditions: island / event / held item per feature, AND inside a combo, OR between
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public final class FeatureConditions {

    static final java.util.Set<String> NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final String SELF = "FeatureConditions";

    static boolean on;
    private static int stamp;
    private static String island = "";
    private static String held = "";
    private static final Map<String, Boolean> events = new HashMap<>();
    private static int ticks;

    private FeatureConditions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of(SELF)
                .tickAlways(FeatureConditions::tick)
                .config("Dev & Debug", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Feature Conditions");
                    b.toggle("Feature Conditions", () -> c.featureConditions, v -> { c.featureConditions = v; changed(); })
                            .desc("Limit any feature to islands, events or a held item. /tmap condition <feature> add island:Garden item:HYPERION "
                                    + "(one line = all must match, several lines = any line). held, invert, remove <n>, clear.")
                            .tags("condition", "island", "only when", "held item");
                    b.info("  /tmap condition lists every rule");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("condition")
                        .executes(c -> listAll(c.getSource()))
                        .then(ClientCommands.argument("feature", StringArgumentType.word())
                                .suggests((c, sb) -> {
                                    String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    for (String n : NAMES) {
                                        if (n.toLowerCase(Locale.ROOT).startsWith(typed) && !n.equals(sb.getRemaining())) sb.suggest(n);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> show(c.getSource(), feature(c)))
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("conditions", StringArgumentType.greedyString())
                                                .suggests((c, sb) -> {
                                                    String r = sb.getRemaining();
                                                    int sp = r.lastIndexOf(' ');
                                                    var sub = sb.createOffset(sb.getStart() + sp + 1);
                                                    String last = r.substring(sp + 1).toLowerCase(Locale.ROOT);
                                                    String area = SkyblockUtils.getArea();
                                                    for (String s : new String[]{"island:" + area.replace(' ', '_'), "item:" + heldId(), "event:"}) {
                                                        if (s.toLowerCase(Locale.ROOT).startsWith(last) && !s.equalsIgnoreCase(last)) sub.suggest(s);
                                                    }
                                                    return sub.buildFuture();
                                                })
                                                .executes(c -> add(c.getSource(), feature(c), StringArgumentType.getString(c, "conditions")))))
                                .then(ClientCommands.literal("held").executes(c -> {
                                    String id = heldId();
                                    if (id.isEmpty()) return fail(c.getSource(), "Hold a SkyBlock item first.");
                                    return add(c.getSource(), feature(c), "item:" + id);
                                }))
                                .then(ClientCommands.literal("invert").executes(c -> {
                                    TeslaMapsConfig.FeatureRule r = rule(feature(c), true);
                                    r.invert = !r.invert;
                                    save();
                                    return show(c.getSource(), feature(c));
                                }))
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                                            TeslaMapsConfig.FeatureRule r = rule(feature(c), false);
                                            int n = IntegerArgumentType.getInteger(c, "n");
                                            if (r == null || n > r.combos.size()) return fail(c.getSource(), "No line " + n + ".");
                                            r.combos.remove(n - 1);
                                            if (r.combos.isEmpty()) cfg().featureConditionRules.remove(feature(c));
                                            save();
                                            return show(c.getSource(), feature(c));
                                        })))
                                .then(ClientCommands.literal("clear").executes(c -> {
                                    cfg().featureConditionRules.remove(feature(c));
                                    save();
                                    return msg(c.getSource(), feature(c) + ": no conditions, always active");
                                }))))));
    }

    static boolean allows(Feature f) {
        if (f.condStamp != stamp) {
            f.condStamp = stamp;
            f.condOk = compute(f.name);
        }
        return f.condOk;
    }

    private static boolean compute(String name) {
        if (SELF.equals(name)) return true;
        TeslaMapsConfig.FeatureRule r = cfg().featureConditionRules.get(name);
        if (r == null || r.combos.isEmpty()) return true;
        boolean match = false;
        for (List<String> combo : r.combos) {
            if (combo.isEmpty()) continue;
            boolean all = true;
            for (String cond : combo) if (!matches(cond)) { all = false; break; }
            if (all) { match = true; break; }
        }
        return match != r.invert;
    }

    private static boolean matches(String cond) {
        int i = cond.indexOf(':');
        if (i < 0) return false;
        String kind = cond.substring(0, i).toLowerCase(Locale.ROOT);
        String value = cond.substring(i + 1).replace('_', ' ').trim();
        return switch (kind) {
            case "island" -> island.equalsIgnoreCase(value);
            case "item" -> held.equalsIgnoreCase(value.replace(' ', '_'));
            case "event" -> Boolean.TRUE.equals(events.get(value.toLowerCase(Locale.ROOT)));
            default -> false;
        };
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        boolean want = c.featureConditions && !c.featureConditionRules.isEmpty();
        if (want != on) { on = want; stamp++; }
        if (!on) return;
        Minecraft mc = Minecraft.getInstance();
        String h = mc.player == null ? "" : ItemUtil.skyblockId(mc.player.getMainHandItem());
        boolean dirty = false;
        if (!h.equals(held)) { held = h; dirty = true; }
        if (ticks++ % 20 == 0) {
            String a = SkyblockUtils.getArea();
            if (!a.equals(island)) { island = a; dirty = true; }
            if (updateEvents(c)) dirty = true;
        }
        if (dirty) stamp++;
    }

    private static boolean updateEvents(TeslaMapsConfig c) {
        List<String> needles = new ArrayList<>();
        for (TeslaMapsConfig.FeatureRule r : c.featureConditionRules.values()) {
            for (List<String> combo : r.combos) for (String cond : combo) {
                if (cond.toLowerCase(Locale.ROOT).startsWith("event:")) {
                    needles.add(cond.substring(6).replace('_', ' ').trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (needles.isEmpty()) {
            boolean had = !events.isEmpty();
            events.clear();
            return had;
        }
        StringBuilder sb = new StringBuilder();
        for (String l : ScoreboardUtils.getScoreboardLines()) sb.append(StringUtil.strip(l)).append('\n');
        for (String l : TabListUtils.getTabListLines()) sb.append(StringUtil.strip(l)).append('\n');
        String all = sb.toString().toLowerCase(Locale.ROOT);
        boolean changed = false;
        Map<String, Boolean> next = new HashMap<>();
        for (String n : needles) next.put(n, !n.isEmpty() && all.contains(n));
        if (!next.equals(events)) {
            events.clear();
            events.putAll(next);
            changed = true;
        }
        return changed;
    }

    static void changed() { stamp++; }

    private static void save() {
        stamp++;
        TeslaMapsConfig.save();
    }

    private static String heldId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "" : ItemUtil.skyblockId(mc.player.getMainHandItem());
    }

    private static String feature(CommandContext<FabricClientCommandSource> c) {
        String typed = StringArgumentType.getString(c, "feature");
        for (String n : NAMES) if (n.equalsIgnoreCase(typed)) return n;
        return typed;
    }

    private static TeslaMapsConfig.FeatureRule rule(String feature, boolean create) {
        Map<String, TeslaMapsConfig.FeatureRule> m = cfg().featureConditionRules;
        TeslaMapsConfig.FeatureRule r = m.get(feature);
        if (r == null && create) m.put(feature, r = new TeslaMapsConfig.FeatureRule());
        return r;
    }

    private static int add(FabricClientCommandSource src, String feature, String text) {
        if (!NAMES.contains(feature)) return fail(src, "Unknown feature " + feature + ".");
        List<String> combo = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            int i = part.indexOf(':');
            String kind = i < 0 ? "" : part.substring(0, i).toLowerCase(Locale.ROOT);
            if (!kind.equals("island") && !kind.equals("item") && !kind.equals("event") || i == part.length() - 1) {
                return fail(src, "Use island:<name>, item:<ID> or event:<text> (spaces as _).");
            }
            combo.add(part);
        }
        rule(feature, true).combos.add(combo);
        save();
        if (!cfg().featureConditions) msg(src, "§eTurn on Feature Conditions (Interface) for this to apply.");
        return show(src, feature);
    }

    private static int show(FabricClientCommandSource src, String feature) {
        TeslaMapsConfig.FeatureRule r = cfg().featureConditionRules.get(feature);
        if (r == null || r.combos.isEmpty()) return msg(src, feature + ": no conditions, always active");
        msg(src, feature + (r.invert ? " §c(inverted: off when a line matches)" : " §7(on when a line matches)"));
        for (int i = 0; i < r.combos.size(); i++) {
            src.sendFeedback(Component.literal("§7 " + (i + 1) + ". §f" + String.join(" §7+§f ", r.combos.get(i))));
        }
        return 1;
    }

    private static int listAll(FabricClientCommandSource src) {
        Map<String, TeslaMapsConfig.FeatureRule> m = cfg().featureConditionRules;
        if (m.isEmpty()) return msg(src, "No feature conditions. /tmap condition <feature> add island:Garden");
        for (String f : m.keySet()) show(src, f);
        return 1;
    }

    private static int msg(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §f" + s));
        return 1;
    }

    private static int fail(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §c" + s));
        return 0;
    }
}
