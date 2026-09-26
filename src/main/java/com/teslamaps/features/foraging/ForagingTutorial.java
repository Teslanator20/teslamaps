package com.teslamaps.features.foraging;

// Ported from SkyHanni's ForagingTutorialQuest.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.navigation.IslandNavigation;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guides a new profile through the Park unlock quests: NPC and next-portal waypoints, plus a path when Island Navigation is on. */
public final class ForagingTutorial {

    private record Step(String quest, String label, String island, Vec3 pos) {}
    private record Next(String island, Vec3 pos, String ending) {}

    private static final Step[] QUESTS = {
            new Step("Foraging Tutorial", "Lumber Jack", "Hub", new Vec3(-123.5, 74.0, -30.0)),
            new Step("Into the Woods", "Charlie", "The Park", new Vec3(-275.9, 80.0, -17.1)),
            new Step("A Helping Hand", "Kelly", "The Park", new Vec3(-350.8, 94.0, 31.7)),
            new Step("The Campfire Cult", "Ryan", "The Park", new Vec3(-362.7, 102.0, -90.5)),
            new Step("The Rebuild", "Melody", "The Park", new Vec3(-412.3, 109.0, 70.2))};

    private static final Next[] NEXT = {
            new Next("The Park", new Vec3(-312.1, 81.0, -9.0), "Can you maybe look for her in the §aSpruce Woods§f?"),
            new Next("The Park", new Vec3(-361.2, 90.0, -14.8), "§cCult Meeting §fthere §c§lRIGHT NOW§f!"),
            new Next("The Park", new Vec3(-397.3, 98.0, -37.5), "§rYou completed each §6Trial of Fire§f! §aCongratulations!"),
            new Next("The Park", new Vec3(-436.4, 110.5, -14.4), " §rTalk to me again if you ever want to give my §dHarp §fa try!"),
            new Next("The Park", new Vec3(-435.5, 110.0, -13.5), "§r§fIt will take me some time to assemble them, so you should §acome back later§f."),
            new Next("The Park", new Vec3(-466.8, 120.0, -41.6), "  §r§fTalk to Molbert"),
            new Next("The Park", new Vec3(-465.9, 119.0, -53.8), "Once you find the ideal spots, go ahead and deploy them."),
            new Next("The Park", new Vec3(-448.8, 120.0, -64.3), "§aPlaced trap §r§7(§r§e1§r§7/§r§a3§r§7)"),
            new Next("The Park", new Vec3(-466.8, 120.0, -43.4), "§aPlaced trap §r§7(§r§a3§r§7/§r§a3§r§7)"),
            new Next("The Park", new Vec3(-435.5, 110.0, -13.5), "/§r§a3§r§7)"),
            new Next("The Park", new Vec3(-485.3, 116.5, -40.7), " §rI hope you forgive me after this and we can still be §6friends§f.")};

    private static final Pattern MUST_COMPLETE = Pattern.compile("You must complete the (.+) Quest to use this!");
    private static final Pattern REQUIRES = Pattern.compile("Requires (.+) Quest");

    private static Vec3 target;
    private static BlockPos targetBlock;
    private static String targetLabel, targetIsland, labelText = "";
    private static boolean navStarted, here;
    private static long lastParkWarp, lastQuestAt;
    private static String lastQuest;
    private static int ticks;

    private ForagingTutorial() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ForagingTutorial").section("Foraging").when(() -> cfg().foragingTutorialGuide)
                .tick(ForagingTutorial::tick)
                .serverChat(ForagingTutorial::onChat)
                .world(ForagingTutorial::render)
                .config("Foraging", b -> {
                    b.label("Park Quests");
                    b.toggle("Foraging Tutorial Guide", () -> cfg().foragingTutorialGuide, v -> cfg().foragingTutorialGuide = v)
                            .desc("New profiles: waypoint to the next Park quest NPC or portal. Draws a path too when Island Navigation is on.")
                            .tags("park", "quest", "tutorial", "lumber jack", "charlie", "kelly", "ryan", "melody");
                    b.button("  Clear Waypoint", ForagingTutorial::clear);
                });
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            if (cmd.trim().equalsIgnoreCase("warp park")) lastParkWarp = System.currentTimeMillis();
        });
    }

    private static void onChat(String raw) {
        String clean = StringUtil.stripTrim(raw);
        if ((clean.equals("You don't have the requirements to use this warp!")
                || clean.equals("You haven't unlocked this fast travel destination!"))
                && System.currentTimeMillis() - lastParkWarp < 1000) {
            start(QUESTS[0]);
            return;
        }
        String area = SkyblockUtils.getArea();
        if (area.equals("Hub") || area.equals("The Park")) {
            Matcher m = MUST_COMPLETE.matcher(clean);
            if (m.find()) {
                byName(m.group(1));
                return;
            }
        }
        for (Next n : NEXT) {
            if (raw.endsWith(n.ending())) {
                set(n.pos(), "Next Quest", n.island());
                msg("§eGo to the next phase!");
                return;
            }
        }
    }

    private static void byName(String quest) {
        for (Step s : QUESTS) {
            if (s.quest().equals(quest)) {
                long now = System.currentTimeMillis();
                if (s.quest().equals(lastQuest) && now - lastQuestAt < 300_000) return;
                start(s);
                return;
            }
        }
    }

    private static void start(Step s) {
        lastQuest = s.quest();
        lastQuestAt = System.currentTimeMillis();
        set(s.pos(), s.label(), s.island());
        msg("§eGo to §a" + s.label() + " §eand start the §6" + s.quest() + " §equest!");
    }

    private static void set(Vec3 pos, String label, String island) {
        target = pos;
        targetBlock = BlockPos.containing(pos);
        targetLabel = label;
        targetIsland = island;
        navStarted = false;
    }

    private static void clear() {
        target = null;
        targetBlock = null;
        targetLabel = null;
        labelText = "";
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (ticks % 40 == 0) scanNpcs();
        if (target == null) return;
        if (ticks % 10 != 0) return;
        String area = SkyblockUtils.getArea();
        here = area.equals(targetIsland);
        if (!here) {
            navStarted = false;
            return;
        }
        double dist = mc.player.position().distanceTo(target);
        if (dist < 4) {
            clear();
            return;
        }
        labelText = "§e" + targetLabel + " §7(" + (int) dist + "m)";
        if (!navStarted && ticks % 20 == 0) navStarted = IslandNavigation.navigate(target, targetLabel);
    }

    private static void scanNpcs() {
        String area = SkyblockUtils.getArea();
        if (!area.equals("Hub") && !area.equals("The Park")) return;
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            String name = StringUtil.stripTrim(s.getName().getString());
            if (!name.startsWith("Requires")) continue;
            Matcher m = REQUIRES.matcher(name);
            if (m.find()) {
                byName(m.group(1));
                return;
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (target == null || !here || labelText.isEmpty()) return;
        ESPRenderer.drawBeaconBeam(pose, targetBlock, 0xFF55FF55, cam);
        ESPRenderer.drawText(pose, labelText, target.add(0, 2.5, 0), 1.0f, cam);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
