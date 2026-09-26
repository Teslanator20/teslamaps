package com.teslamaps.features.rift;

// After SkyHanni's TimiteTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.features.FishingHelpers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Youngite / Timite / Obsolite mined in the Rift, in motes, plus craftable Highlite and rift time gained. */
public final class TimiteTracker {

    private static final String[] ORES = {"YOUNGITE", "TIMITE", "OBSOLITE"};
    private static final int[] MOTES = {50, 100, 200};
    // Highlite = 32 Youngite + 32 Timite + 16 Obsolite (NEU recipe)
    private static final int[] HIGHLITE_NEED = {32, 32, 16};
    private static final int HIGHLITE_MOTES = 25_000;
    private static final Set<String> TOOLS = Set.of("ANTI_SENTIENT_PICKAXE", "EON_PICKAXE", "CHRONO_PICKAXE", "TIME_GUN");

    private static final Set<String> MOUNTAINTOP = Set.of("Continuum", "The Mountaintop", "Trial Grounds", "Time-Torn Isles",
            "Wizardman Bureau", "Wizard Brawl", "Walk of Fame", "Time Chamber");

    private static Tracker tracker;
    private static final int[] last = new int[3];
    private static boolean primed, show;
    private static Object lastLevel;
    private static int areaTicks;
    private static boolean onMountaintop;

    private TimiteTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        tracker = Tracker.create("timite", "§9§lTimite Tracker");
        for (int i = 0; i < ORES.length; i++) tracker.fixedValue(ORES[i], MOTES[i]);
        tracker.extra(v -> {
            List<String> out = new ArrayList<>();
            long craftable = Long.MAX_VALUE;
            for (int i = 0; i < ORES.length; i++) craftable = Math.min(craftable, tracker.count(ORES[i], v) / HIGHLITE_NEED[i]);
            if (craftable > 0) out.add(" §7" + craftable + "x §5Highlite §7craftable: §d" + ItemPrice.format(craftable * HIGHLITE_MOTES) + " motes");
            long secs = tracker.count("TIMITE", v) * 2;
            out.add("§aTime§7: §a" + (secs >= 60 ? secs / 60 + "m " : "") + secs % 60 + "sф");
            out.add("§7(profit in §dmotes§7)");
            return out;
        });
        HudEditElement.of("riftTimiteTracker", "Timite Tracker").ctx(HudEditElement.HudCtx.ANY).section("Rift")
                .enabled(() -> cfg().riftTimiteTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("TimiteTracker").section("Rift").when(() -> cfg().riftTimiteTracker)
                .tick(TimiteTracker::tick)
                .hud("rifttimitetracker", TimiteTracker::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Mountaintop");
                    b.toggle("Timite Tracker", () -> c.riftTimiteTracker, v -> c.riftTimiteTracker = v)
                            .desc("Youngite, Timite and Obsolite mined on the Mountaintop with their motes value, craftable Highlite "
                                    + "and the rift time gained. Move it in /tmap gui, reset with /tmap tracker timite reset.")
                            .tags("youngite", "obsolite", "highlite", "motes", "mountaintop", "tracker");
                    b.toggle("  Only While Holding Tool", () -> c.riftTimiteTrackerToolOnly, v -> c.riftTimiteTrackerToolOnly = v)
                            .desc("Only shown with a Timite pickaxe or the Time Gun in hand");
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{tracker::resetSession, tracker::resetTotal, tracker::toggleView});
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        tracker.tick();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            primed = false;
        }
        boolean rift = SkyblockUtils.isInArea("The Rift");
        if (++areaTicks >= 20) {
            areaTicks = 0;
            onMountaintop = rift && MOUNTAINTOP.contains(FishingHelpers.subArea());
        }
        show = onMountaintop && (!cfg().riftTimiteTrackerToolOnly || TOOLS.contains(ItemUtil.skyblockId(mc.player.getMainHandItem())));
        if (!rift) {
            primed = false;
            return;
        }
        int[] now = counts(mc.player.getInventory());
        if (primed && !(mc.screen instanceof AbstractContainerScreen<?>)) {
            for (int i = 0; i < ORES.length; i++) {
                int d = now[i] - last[i];
                if (d > 0 && d < 64) tracker.add(ORES[i], d);
            }
        }
        System.arraycopy(now, 0, last, 0, 3);
        primed = true;
    }

    private static int[] counts(Inventory inv) {
        int[] out = new int[3];
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = ItemUtil.skyblockId(s);
            for (int j = 0; j < ORES.length; j++) if (ORES[j].equals(id)) out[j] += s.getCount();
        }
        return out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        tracker.draw(g, mc, c.riftTimiteTrackerX, c.riftTimiteTrackerY, c.riftTimiteTrackerScale);
    }
}
