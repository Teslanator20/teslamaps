package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/qolplus/vesuvius/Jewing.kt + ProfitData.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CroesusProfit;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** Kuudra chest profit over time: loot value minus key and kismet, chests, runs and fails. */
public final class KuudraProfitTracker {

    static final String LOOT = "kuudra_loot", COST = "kuudra_cost", PAID = "kuudra_paid", FREE = "kuudra_free",
            RUNS = "kuudra_runs", FAILS = "kuudra_fails";

    private static Tracker tracker;
    private static HudEditElement hud;

    private static long pendingValue, pendingCost;
    private static boolean pendingPaid, pendingInfernal;
    private static long pendingAt;
    private static boolean resetDown, viewDown;
    private static long runTimeSum;
    private static int runTimeCount;
    private static int ticks;

    private KuudraProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("kuudra_profit", "§c§lKuudra Profit")
                .priceSource(() -> cfg().croesusPriceSource)
                .idleSeconds(() -> 600)
                .fixedValue(LOOT, 1).coinKey(LOOT).name(LOOT, "§aChest Loot")
                .fixedValue(COST, -1).coinKey(COST).name(COST, "§cKeys + Kismet")
                .fixedValue(PAID, 0).name(PAID, "§ePaid Chests")
                .fixedValue(FREE, 0).name(FREE, "§aFree Chests")
                .fixedValue(RUNS, 0).name(RUNS, "§fRuns")
                .fixedValue(FAILS, 0).name(FAILS, "§cFailed Runs")
                .extra(KuudraProfitTracker::extra);
        hud = HudEditElement.of("kuudraProfitTracker", "Kuudra Profit Tracker")
                .enabled(() -> cfg().kuudraProfitTracker)
                .size(() -> Math.max(90, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("KuudraProfitTracker").when(() -> cfg().kuudraProfitTracker)
                .tick(KuudraProfitTracker::tick)
                .serverChat(KuudraProfitTracker::onChat)
                .hud("kuudraprofittracker", KuudraProfitTracker::render)
                .config("Croesus", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Kuudra Profit Tracker", () -> c.kuudraProfitTracker, v -> c.kuudraProfitTracker = v)
                            .desc("Adds up every opened Kuudra chest: loot value minus key and Kismet cost (settings above), "
                                    + "paid/free chests, runs and failed runs, profit per hour. Session and total, saved. Move it in /tmap gui.")
                            .tags("kuudra", "profit", "vesuvius", "tracker");
                    b.toggle("  Show Outside Kuudra / Crimson Isle", () -> c.kuudraProfitEverywhere, v -> c.kuudraProfitEverywhere = v);
                    b.keybind("  Reset Session Key", () -> c.kuudraProfitResetKey, v -> c.kuudraProfitResetKey = v);
                    b.keybind("  Session / Total Key", () -> c.kuudraProfitViewKey, v -> c.kuudraProfitViewKey = v);
                    b.trackerButtons(tracker);
                });
    }

    private static List<String> extra(Tracker.View v) {
        long chests = tracker.count(PAID, v) + tracker.count(FREE, v);
        if (chests == 0) return List.of();
        String avg = "§eper chest: §6" + ItemPrice.format(tracker.profit(v) / chests);
        if (v != Tracker.View.SESSION || runTimeCount == 0) return List.of(avg);
        long s = runTimeSum / runTimeCount / 1000;
        return List.of(avg, "§eavg run: §f" + s / 60 + "m " + (s % 60 < 10 ? "0" : "") + s % 60 + "s");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean canPress = mc.screen == null;
        boolean r = canPress && c.kuudraProfitResetKey != -1 && KeyNames.isDown(c.kuudraProfitResetKey);
        if (r && !resetDown) {
            tracker.resetSession();
            runTimeSum = 0;
            runTimeCount = 0;
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Kuudra profit session reset."));
        }
        resetDown = r;
        boolean v = canPress && c.kuudraProfitViewKey != -1 && KeyNames.isDown(c.kuudraProfitViewKey);
        if (v && !viewDown) tracker.toggleView();
        viewDown = v;

        if (mc.screen instanceof AbstractContainerScreen<?> cs && ++ticks % 5 == 0) snapshot(cs);
        if (KuudraContext.inKuudra()) tracker.markActivity();
        tracker.tick();
    }

    // Kuudra reward chest GUI: "Paid Chest" / "Free Chest", the Open Reward Chest button carries the lore
    private static void snapshot(AbstractContainerScreen<?> cs) {
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        boolean paid = title.equals("Paid Chest");
        if (!paid && !title.equals("Free Chest")) return;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemLore lore = slot.getItem().get(DataComponents.LORE);
            if (lore == null) continue;
            boolean contents = false, infernal = false;
            for (Component l : lore.lines()) {
                String s = StringUtil.stripTrim(l.getString());
                if (s.equals("Contents")) contents = true;
                else if (s.equals("Infernal Kuudra Key")) infernal = true;
            }
            if (!contents) continue;
            CroesusProfit.ChestValue v = CroesusProfit.evaluate(lore);
            double value = v.value() - (cfg().croesusNoEssence ? v.essence() : 0);
            pendingValue = Math.round(value);
            pendingCost = Math.round(v.cost());
            pendingPaid = paid;
            pendingInfernal = infernal;
            pendingAt = System.currentTimeMillis();
            return;
        }
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.contains("CHEST REWARDS")) {
            boolean paid = s.contains("PAID") || !s.contains("FREE") && pendingPaid;
            if (pendingAt != 0 && System.currentTimeMillis() - pendingAt < 15_000) {
                tracker.add(LOOT, pendingValue);
                tracker.add(COST, pendingCost);
                KuudraRunTrackers.onChestOpened(paid && pendingInfernal);
            } else {
                KuudraRunTrackers.onChestOpened(false);
            }
            tracker.add(paid ? PAID : FREE, 1);
            pendingAt = 0;
            return;
        }
        if (!KuudraContext.inKuudra()) return;
        if (s.equals("KUUDRA DOWN!")) {
            tracker.add(RUNS, 1);
            long start = KuudraContext.runStart();
            if (start > 0) {
                runTimeSum += System.currentTimeMillis() - start;
                runTimeCount++;
            }
        } else if (s.equals("DEFEAT")) {
            tracker.add(FAILS, 1);
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!cfg().kuudraProfitEverywhere) {
            String area = SkyblockUtils.getArea();
            if (!area.contains("Kuudra") && !area.contains("Crimson Isle")) return;
        }
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
