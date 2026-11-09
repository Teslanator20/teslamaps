package com.teslamaps.features;

// Ported from SkyHanni's garden/tracker/RareCropTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counts RARE CROP! drops (Cropie, Squash, Fermento, pest crop items …) with their value. */
public final class RareCropTracker {

    // {item id, display name}
    private static final String[][] DROPS = {
            {"CROPIE", "§aCropie"}, {"SQUASH", "§9Squash"}, {"FERMENTO", "§5Fermento"},
            {"HELIANTHUS", "§6Helianthus"}, {"SEASONING", "§2Seasoning"},
            {"CORNUCOPIA", "§aCornucopia"}, {"CARROT_ZEST", "§aCarrot Zest"}, {"DEEPFRIES", "§aDeepfries"},
            {"AGGOURDIAN", "§aAggourdian"}, {"CANE_KNOT", "§aCane Knot"}, {"MELON_JUICE", "§aMelon Juice"},
            {"CACTUS_FLOWER", "§aCactus Flower"}, {"DESIGNER_COFFEE_BEANS", "§aDesigner Coffee Beans"},
            {"FEASTFUNGUS", "§aFeastfungus"}, {"BOTROOT", "§aBotroot"},
            {"SALTED_SUNFLOWER_SEEDS", "§aSalted Sunflower Seeds"}, {"CRYSTALIZED_MOONLIGHT", "§aCrystalized Moonlight"},
            {"FLORAL_GELATIN", "§aFloral Gelatin"}, {"RAREFINDER_CHIP", "§9Rarefinder Chip"},
            {"BURROWING_SPORES", "§9Burrowing Spores"}, {"WARTY", "§5Warty"},
            {"COMPOST", "§aCompost"}, {"PLANT_MATTER", "§aPlant Matter"}, {"DUNG", "§aDung"},
            {"HONEY_JAR", "§aHoney Jar"}, {"CHEESE_FUEL", "§aTasty Cheese"}, {"JELLY", "§aJelly"},
    };
    private static final Pattern DROP;

    static {
        StringBuilder sb = new StringBuilder("^(?:VERY )?RARE CROP! (");
        for (int i = 0; i < DROPS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(StringUtil.strip(DROPS[i][1])));
        }
        DROP = Pattern.compile(sb.append(")(?: .*)?$").toString());
    }

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean show;

    private RareCropTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("rare_crops", "§6§lRare Crop Tracker")
                .priceSource(() -> cfg().rareCropPriceSource);
        for (String[] d : DROPS) tracker.name(d[0], d[1]);
        hud = HudEditElement.of("rareCropTracker", "Rare Crop Tracker").section("Garden").ctx(HudEditElement.HudCtx.GARDEN)
                .enabled(() -> cfg().rareCropTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("RareCropTracker").section("Garden").when(() -> cfg().rareCropTracker)
                .tick(RareCropTracker::tick)
                .serverChat(RareCropTracker::onChat)
                .hud("rarecroptracker", RareCropTracker::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Rare Crops");
                    b.toggle("Rare Crop Tracker", () -> c.rareCropTracker, v -> c.rareCropTracker = v)
                            .desc("Counts RARE CROP! drops (Cropie, Squash, Fermento, Helianthus, pest crop items) with their value, per session and all time (/tmap gui)")
                            .tags("cropie", "squash", "fermento", "helianthus", "rare crop", "armor drop");
                    b.toggle("  Only With Farming Tool", () -> c.rareCropOnlyWithTool, v -> c.rareCropOnlyWithTool = v);
                    b.toggle("  Hide RARE CROP! Chat", () -> c.rareCropHideChat, v -> c.rareCropHideChat = v)
                            .desc("Needs the Chat section on");
                    b.priceSource("  Price Source", () -> c.rareCropPriceSource, v -> c.rareCropPriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static void tick() {
        tracker.tick();
        Minecraft mc = Minecraft.getInstance();
        show = SkyblockUtils.isInGarden()
                && (!cfg().rareCropOnlyWithTool || FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem()));
    }

    private static void onChat(String text) {
        Matcher m = DROP.matcher(StringUtil.stripTrim(text));
        if (!m.matches()) return;
        String name = m.group(1);
        for (String[] d : DROPS) {
            if (StringUtil.strip(d[1]).equals(name)) {
                tracker.add(d[0], 1);
                return;
            }
        }
    }

    /** From the chat filter: drop the RARE CROP! line. */
    static boolean hide(String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.rareCropTracker || !c.rareCropHideChat || !c.section("Garden")) return false;
        return DROP.matcher(stripped.trim()).matches();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
