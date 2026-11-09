package com.teslamaps.features.theend;

// Ported from SkyHanni's features/misc/DraconicSacrificeTracker.kt
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
import net.minecraft.world.phys.AABB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DraconicSacrificeTracker {

    private static final Pattern SACRIFICE = Pattern.compile("^SACRIFICE! You turned (.+) into (\\d+) Dragon Essence!$");
    private static final Pattern BONUS = Pattern.compile("^BONUS LOOT! You also received (?:([\\d,]+)x )?(.+) from your sacrifice!$");
    private static final AABB ALTAR = new AABB(-601, 4, -282, -586, 15, -269);
    private static final String ESSENCE = "ESSENCE_DRAGON";
    private static final String SACRIFICES = "SACRIFICED_ITEMS";

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean show;

    private DraconicSacrificeTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("draconic_sacrifice", "§5§lDraconic Sacrifice")
                .priceSource(() -> cfg().draconicPriceSource)
                .name(SACRIFICES, "§bItems Sacrificed")
                .name(ESSENCE, "§dDragon Essence");
        hud = HudEditElement.of("draconicTracker", "Draconic Sacrifice Tracker").section("The End")
                .enabled(() -> cfg().draconicTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("DraconicSacrificeTracker").section("The End").when(() -> cfg().draconicTracker)
                .tick(DraconicSacrificeTracker::tick)
                .serverChat(DraconicSacrificeTracker::onChat)
                .hud("draconictracker", DraconicSacrificeTracker::render)
                .config("The End", b -> {
                    b.label("Draconic Sacrifice");
                    b.toggle("Draconic Sacrifice Tracker", () -> cfg().draconicTracker, v -> cfg().draconicTracker = v)
                            .desc("Counts sacrificed items, Dragon Essence and bonus loot with profit. Shown in The End (/tmap gui)");
                    b.toggle("  Only At The Altar", () -> cfg().draconicOnlyAltar, v -> cfg().draconicOnlyAltar = v);
                    b.priceSource("  Price Source", () -> cfg().draconicPriceSource, v -> cfg().draconicPriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static void tick() {
        tracker.tick();
        Minecraft mc = Minecraft.getInstance();
        show = SkyblockUtils.isInArea("The End")
                && (!cfg().draconicOnlyAltar || ALTAR.contains(mc.player.position()));
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("SACRIFICE!")) {
            Matcher m = SACRIFICE.matcher(t);
            if (!m.matches()) return;
            tracker.add(ESSENCE, Long.parseLong(m.group(2)));
            tracker.add(SACRIFICES, 1);
        } else if (t.startsWith("BONUS LOOT!")) {
            Matcher m = BONUS.matcher(t);
            if (!m.matches()) return;
            long amount = m.group(1) == null ? 1 : Long.parseLong(m.group(1).replace(",", ""));
            String name = m.group(2).trim();
            String id = PriceManager.idForName(name);
            if (id == null) id = SkyblockItems.idForName(name);
            tracker.add(id != null ? id : name, amount);
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
