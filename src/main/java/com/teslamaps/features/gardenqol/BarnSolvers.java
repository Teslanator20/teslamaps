package com.teslamaps.features.gardenqol;

// Ported from Skyblocker's barn/HungryHiker.java and barn/TreasureHunter.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BarnSolvers {

    private static final Pattern HIKER = Pattern.compile(
            "^\\[NPC] Hungry Hiker: (The food I want is|(I asked for) food that is) ([a-zA-Z, '\\-]*\\.)$");
    private static final Pattern HUNTER = Pattern.compile("^\\[NPC] Treasure Hunter: ([a-zA-Z, '\\-.]*)$");

    private static final Map<String, String> FOODS = new HashMap<>();
    private static final Map<String, BlockPos> SPOTS = new HashMap<>();

    private static final WaypointSet waypoints = new WaypointSet().beam(true).box(true).label(true)
            .distance(true).throughWalls(true).removeWithin(4);

    private BarnSolvers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BarnSolvers").when(() -> cfg().hungryHikerSolver || cfg().treasureHunterSolver)
                .serverChat(BarnSolvers::onChat)
                .tick(waypoints::tick)
                .world(BarnSolvers::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Hungry Hiker Solver", () -> c.hungryHikerSolver, v -> c.hungryHikerSolver = v)
                            .desc("The Farming Islands: names the food the Hungry Hiker describes.")
                            .tags("hiker", "barn", "farming islands", "solver");
                    b.toggle("Treasure Hunter Solver", () -> c.treasureHunterSolver, v -> c.treasureHunterSolver = v)
                            .desc("The Farming Islands: turns the Treasure Hunter's hint into a waypoint at the treasure.")
                            .tags("treasure hunter", "barn", "farming islands", "solver", "waypoint");
                });
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (!text.startsWith("[NPC] ")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg().hungryHikerSolver) {
            Matcher m = HIKER.matcher(text);
            if (m.matches()) {
                String food = FOODS.get(m.group(3));
                if (food != null) {
                    mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eHungry Hiker §fwants §a" + food));
                }
                return;
            }
        }
        if (cfg().treasureHunterSolver) {
            Matcher m = HUNTER.matcher(text);
            if (!m.matches()) return;
            BlockPos pos = SPOTS.get(m.group(1));
            if (pos == null) return;
            waypoints.clear();
            waypoints.add(pos, "§6Treasure", 0xFFFFAA00, 10 * 60_000L);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eTreasure Hunter§f: dig around §a"
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (cfg().treasureHunterSolver) waypoints.render(pose, cam);
    }

    private static void spot(String hint, int x, int y, int z) {
        SPOTS.put(hint, new BlockPos(x, y, z));
    }

    static {
        FOODS.put("from a cow.", "Cooked Beef");
        FOODS.put("meat from a fowl.", "Cooked Chicken");
        FOODS.put("red on the inside, green on the outside.", "Melon Slice");
        FOODS.put("a cooked potato.", "Baked Potato");
        FOODS.put("a stew.", "Rabbit Stew");
        FOODS.put("a grilled meat.", "Cooked Porkchop");
        FOODS.put("red and crunchy.", "Apple");
        FOODS.put("made of wheat.", "Bread");

        spot("There's a treasure chest somewhere in a small cave in the gorge.", 258, 70, -492);
        spot("I was in the desert earlier, and I saw something near a red sand rock.", 357, 82, -319);
        spot("There's this guy who collects animals to experiment on, I think I saw something near his house.", 259, 184, -564);
        spot("There's a small house in the gorge, I saw some treasure near there.", 297, 87, -562);
        spot("There's this guy who says he has the best sheep in the world. I think I saw something around his hut.", 392, 85, -372);
        spot("I spotted something by an odd looking mushroom on one of the ledges in the Mushroom Gorge, you should check it out.", 305, 73, -557);
        spot("There are some small ruins out in the desert, might want to check them out.", 320, 102, -471);
        spot("Some dirt was kicked up by the water pool in the overgrown Mushroom Cave. Have a look over there.", 234, 56, -410);
        spot("There are some old stone structures in the Mushroom Gorge, give them a look.", 223, 54, -503);
        spot("In the Mushroom Gorge where blue meets the ceiling and floor, you will find what you are looking for.", 205, 42, -527);
        spot("There was a haystack with a crop greener than usual around it, I think there is something near there.", 334, 82, -389);
        spot("There's a single piece of tall grass growing in the desert, I saw something there.", 283, 76, -363);
        spot("I saw some treasure by a cow skull near the village.", 141, 77, -397);
        spot("Near a melon patch inside a tunnel in the mountain I spotted something.", 257, 100, -569);
        spot("I saw something near a farmer's cart, you should check it out.", 155, 90, -591);
        spot("I remember there was a stone pillar made only of cobblestone in the oasis, could be something there.", 122, 66, -409);
        spot("I thought I saw something near the smallest stone pillar in the oasis.", 94, 65, -455);
        spot("I found something by a mossy stone pillar in the oasis, you should take a look.", 179, 93, -537);
        spot("Down in the glowing Mushroom Cave, there was a weird looking mushroom, check it out.", 182, 44, -451);
        spot("Something caught my eye by the red sand near the bridge over the gorge.", 306, 105, -489);
        spot("I seem to recall seeing something near the well in the village.", 170, 77, -375);
        spot("I was down near the lower oasis yesterday, I think I saw something under the bridge.", 142, 69, -448);
        spot("I was at the upper oasis today, I recall seeing something on the cobblestone stepping stones.", 188, 77, -459);
    }
}
