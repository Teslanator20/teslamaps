package com.teslamaps.features;

// Ported from SkyHanni's CropFeverTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Crop Fever count, time, breaks during and outside and the fever drops. */
public final class CropFeverTracker {

    private static final Pattern DROP = Pattern.compile("^(UNCOMMON|RARE|CRAZY RARE|PRAY TO RNGESUS) DROP! You dropped (\\d+)x (.+)!$");
    private static final String[] RARITIES = {"UNCOMMON", "RARE", "CRAZY RARE", "PRAY TO RNGESUS"};
    private static final String[] RARITY_NAMES = {"§a§lUNCOMMON DROP", "§9§lRARE DROP", "§d§lCRAZY RARE DROP", "§5§lPRAY TO RNGESUS DROP"};

    private static Tracker tracker, stats;
    private static HudEditElement hud;
    private static boolean fever;
    private static long feverSince;
    private static boolean show;
    private static Object lastLevel;
    private static int ticks;
    private static boolean holdingFever;

    private CropFeverTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        stats = Tracker.create("crop_fever_stats", "Crop Fever Stats")
                .fixedValue("fevers", 0).fixedValue("during", 0).fixedValue("outside", 0).fixedValue("fever_ms", 0);
        for (String r : RARITIES) stats.fixedValue("rng_" + r, 0);
        tracker = Tracker.create("crop_fever", "§6§lCrop Fever Tracker")
                .priceSource(() -> cfg().cropFeverPriceSource)
                .extra(CropFeverTracker::extraLines);
        hud = HudEditElement.of("cropFeverTracker", "Crop Fever Tracker").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().cropFeverTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("CropFeverTracker").section("Garden").when(() -> cfg().cropFeverTracker)
                .tick(CropFeverTracker::tick)
                .serverChat(CropFeverTracker::onChat)
                .hudHiddenInInventory("cropfevertracker", CropFeverTracker::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Fever");
                    b.toggle("Crop Fever Tracker", () -> c.cropFeverTracker, v -> c.cropFeverTracker = v)
                            .desc("Counts Crop Fevers, their time, blocks broken during and outside a fever and "
                                    + "every fever drop with blocks per drop. Move it in /tmap gui.")
                            .tags("fever", "rng", "drops");
                    b.toggle("  Only With Crop Fever Tool", () -> c.cropFeverOnlyTool, v -> c.cropFeverOnlyTool = v);
                    b.toggle("  Only During A Fever", () -> c.cropFeverOnlyDuring, v -> c.cropFeverOnlyDuring = v);
                    b.priceSource("  Price Source", () -> c.cropFeverPriceSource, v -> c.cropFeverPriceSource = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{
                                    () -> { tracker.resetSession(); stats.resetSession(); },
                                    () -> { tracker.resetTotal(); stats.resetTotal(); },
                                    tracker::toggleView});
                });
    }

    private static List<String> extraLines(Tracker.View view) {
        List<String> out = new ArrayList<>();
        long fevers = stats.count("fevers", view);
        long during = stats.count("during", view), outside = stats.count("outside", view);
        out.add("§7Crop Fevers: §e" + GardenMilestoneData.sep(fevers)
                + (fevers > 0 ? " §7(§e" + GardenMilestoneData.sep(outside / fevers) + " §7breaks each)" : ""));
        out.add("§7Fever Time: §b" + time(stats.count("fever_ms", view) + (fever ? System.currentTimeMillis() - feverSince : 0)));
        out.add("§7Crops Broken: §e" + GardenMilestoneData.sep(during + outside)
                + " §7(during §e" + GardenMilestoneData.sep(during) + "§7)");
        for (int i = 0; i < RARITIES.length; i++) {
            long n = stats.count("rng_" + RARITIES[i], view);
            if (n == 0) continue;
            out.add("§7- §e" + n + "x " + RARITY_NAMES[i] + " §7(" + GardenMilestoneData.sep(during / n) + " breaks/drop)");
        }
        return out;
    }

    private static String time(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return (s / 3600) + "h " + (s / 60 % 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    /** From GardenMilestoneData, one crop broken in the garden. */
    public static void onCropBreak(String crop) {
        stats.add(fever ? "during" : "outside", 1);
        tracker.markActivity();
    }

    private static void tick() {
        tracker.tick();
        stats.tick();
        Minecraft mc = Minecraft.getInstance();
        if (lastLevel != mc.level) {
            lastLevel = mc.level;
            stopFever();
        }
        if (++ticks % 10 == 0) holdingFever = hasCropFever(mc.player.getMainHandItem());
        TeslaMapsConfig c = cfg();
        show = SkyblockUtils.isInGarden() && (!c.cropFeverOnlyTool || holdingFever) && (!c.cropFeverOnlyDuring || fever);
    }

    private static boolean hasCropFever(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getCompoundOrEmpty("enchantments").contains("ultimate_crop_fever");
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("WOAH! You caught a case of the CROP FEVER")) {
            if (!fever) {
                fever = true;
                feverSince = System.currentTimeMillis();
                stats.add("fevers", 1);
            }
            return;
        }
        if (t.startsWith("GONE! Your CROP FEVER has been cured!")) {
            stopFever();
            return;
        }
        if (!fever || !t.contains(" DROP! You dropped ")) return;
        Matcher m = DROP.matcher(t);
        if (!m.matches()) return;
        long amount = Long.parseLong(m.group(2));
        String name = m.group(3).trim();
        String id = PriceManager.idForName(name);
        if (id == null) id = SkyblockItems.idForName(name);
        tracker.add(id != null ? id : name, amount);
        stats.add("rng_" + m.group(1), 1);
    }

    private static void stopFever() {
        if (!fever) return;
        fever = false;
        stats.add("fever_ms", System.currentTimeMillis() - feverSince);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
