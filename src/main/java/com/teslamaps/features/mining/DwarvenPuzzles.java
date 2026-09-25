package com.teslamaps.features.mining;

// Ported from Skyblocker's Puzzler.java, Fetchur.java and fossil/FossilMuncher.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Puzzler target block, Fetchur's item and the Fossil Muncher's fossil instead of their riddles. */
public final class DwarvenPuzzles {

    private static final Pattern PUZZLER = Pattern.compile("^\\[NPC] Puzzler: ([▲▶◀▼]{10})$");
    private static final Pattern FETCHUR = Pattern.compile("^\\[NPC] Fetchur: (?:its|theyre) ([a-zA-Z, \\-]*)$");
    private static final Pattern FOSSIL = Pattern.compile("^\\[NPC] Fossil Muncher: the fossil i want ([a-zA-Z, \\-]*)$");
    private static final Map<String, String> FETCHUR_ANSWERS = new HashMap<>();
    private static final Map<String, String> FOSSIL_ANSWERS = Map.of(
            "lived underground and dug tunnels", "Claw Fossil",
            "had a really fancy tail", "Clubbed Fossil",
            "was the king of his kind", "Footprint Fossil",
            "lived underwater", "Helix Fossil",
            "was kinda spiny u know", "Spine Fossil",
            "lived in herds and was quite woolly", "Tusk Fossil",
            "is pretty rough to look at", "Ugly Fossil",
            "had a really pointy beak", "Webbed Fossil");

    static {
        FETCHUR_ANSWERS.put("yellow and see through", "Yellow Stained Glass");
        FETCHUR_ANSWERS.put("circular and sometimes moves", "Compass");
        FETCHUR_ANSWERS.put("expensive minerals", "Mithril");
        FETCHUR_ANSWERS.put("useful during celebrations", "Firework Rocket");
        FETCHUR_ANSWERS.put("hot and gives energy", "Cheap / Decent / Black Coffee");
        FETCHUR_ANSWERS.put("tall and can be opened", "Any Wooden Door / Iron Door");
        FETCHUR_ANSWERS.put("brown and fluffy", "Rabbit's Foot");
        FETCHUR_ANSWERS.put("explosive but more than usual", "Superboom TNT");
        FETCHUR_ANSWERS.put("wearable and grows", "Pumpkin");
        FETCHUR_ANSWERS.put("shiny and makes sparks", "Flint and Steel");
        FETCHUR_ANSWERS.put("green and some dudes trade stuff for it", "Emerald");
        FETCHUR_ANSWERS.put("red and soft", "Red Wool");
    }

    private static Feature feature;
    private static BlockPos target;
    private static AABB box;
    private static long targetAt;

    private DwarvenPuzzles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("DwarvenPuzzles").section("Mining").when(() -> cfg().npcSolvers)
                .serverChat(DwarvenPuzzles::onChat)
                .world(DwarvenPuzzles::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Dwarven NPC Solvers", () -> c.npcSolvers, v -> c.npcSolvers = v)
                            .desc("Solves the daily NPC riddles in the Dwarven Mines and Glacite Tunnels")
                            .tags("puzzler", "fetchur", "fossil muncher", "riddle", "solver", "npc");
                    b.toggle("  Puzzler", () -> c.npcSolverPuzzler, v -> c.npcSolverPuzzler = v)
                            .desc("Marks the block the arrows lead to");
                    b.color("    Puzzler Color", () -> c.npcSolverPuzzlerColor, v -> c.npcSolverPuzzlerColor = v);
                    b.toggle("  Fetchur", () -> c.npcSolverFetchur, v -> c.npcSolverFetchur = v)
                            .desc("Shows the wanted item instead of the riddle");
                    b.toggle("  Fossil Muncher", () -> c.npcSolverFossil, v -> c.npcSolverFossil = v)
                            .desc("Shows the wanted fossil instead of the riddle");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("Puzzler gave you ")) {
            target = null;
            return;
        }
        if (!cfg().npcSolverPuzzler || !t.startsWith("[NPC] Puzzler: ")) return;
        Matcher m = PUZZLER.matcher(t);
        if (!m.matches()) return;
        int x = 181, z = 135;
        for (char c : m.group(1).toCharArray()) {
            if (c == '▲') z++;
            else if (c == '▼') z--;
            else if (c == '◀') x++;
            else if (c == '▶') x--;
        }
        target = new BlockPos(x, 195, z);
        box = new AABB(target);
        targetAt = System.currentTimeMillis();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB box = DwarvenPuzzles.box;
        if (target == null || box == null || !cfg().npcSolverPuzzler) return;
        if (System.currentTimeMillis() - targetAt > 300_000) {
            target = null;
            return;
        }
        int color = TeslaMapsConfig.parseColor(cfg().npcSolverPuzzlerColor);
        ESPRenderer.drawFilledBox(pose, box, (color & 0x00FFFFFF) | 0x50000000, cam, true);
        ESPRenderer.drawBoxOutline(pose, box, color, 3f, cam, true);
    }

    /** Swaps Fetchur's and the Fossil Muncher's riddle for the answer. From ChatMixin. */
    public static Component rewrite(Component msg) {
        if (feature == null || (!cfg().npcSolverFetchur && !cfg().npcSolverFossil) || !feature.active()) return msg;
        String raw = msg.getString();
        if (raw.indexOf("[NPC] F") < 0) return msg;
        String t = StringUtil.stripTrim(raw);
        if (cfg().npcSolverFetchur && t.startsWith("[NPC] Fetchur: ")) {
            Matcher m = FETCHUR.matcher(t);
            if (m.matches()) {
                String a = FETCHUR_ANSWERS.getOrDefault(m.group(1), m.group(1));
                return Component.literal("§e[NPC] Fetchur§f: §a" + a).withStyle(s -> s.withHoverEvent(
                        new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7" + t))));
            }
        }
        if (cfg().npcSolverFossil && t.startsWith("[NPC] Fossil Muncher: ")) {
            Matcher m = FOSSIL.matcher(t);
            if (m.matches()) {
                String a = FOSSIL_ANSWERS.getOrDefault(m.group(1), m.group(1));
                return Component.literal("§e[NPC] §6Fossil Muncher§f: §a" + a).withStyle(s -> s.withHoverEvent(
                        new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7" + t))));
            }
        }
        return msg;
    }
}
