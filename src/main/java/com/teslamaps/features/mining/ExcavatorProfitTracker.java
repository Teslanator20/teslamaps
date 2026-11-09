package com.teslamaps.features.mining;

// Ported from SkyHanni's fossilexcavator/ExcavatorProfitTracker.kt, ProfitPerExcavation.kt,
// GlacitePowderFeatures.kt and FossilExcavatorApi.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fossil Excavator loot minus the Suspicious Scrap, per excavation, plus the powder amount as slot text. */
public final class ExcavatorProfitTracker {

    private static final String START = "EXCAVATION COMPLETE";
    private static final String EMPTY = "You didn't find anything. Maybe next time!";
    private static final Pattern END = Pattern.compile("^▬{64}$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:\\+?([\\d,]+)x )?(.+?)(?: ?x([\\d,]+))?$");
    private static final Pattern POWDER_SLOT = Pattern.compile("^Glacite Powder x([\\d,]+)$");
    private static final String SCRAP = "SUSPICIOUS_SCRAP";
    private static final String RUNS = "EXCAVATOR_RUNS", USED = "EXCAVATOR_SCRAP", DUST = "EXCAVATOR_FOSSIL_DUST",
            POWDER = "EXCAVATOR_GLACITE_POWDER";

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean inLoot, inCenter, inMenu;
    private static int ticks;
    private static final Map<String, Long> loot = new LinkedHashMap<>();
    private static AbstractContainerScreen<?> menu;
    private static final String[] slotText = new String[54];

    private ExcavatorProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("fossil_excavator", "§e§lFossil Excavation Profit")
                .priceSource(() -> cfg().excavatorPriceSource)
                .fixedValue(RUNS, 0).name(RUNS, "§eTimes Excavated")
                .name(USED, "§cSuspicious Scrap Used")
                .fixedValue(POWDER, 0).name(POWDER, "§bGlacite Powder")
                .name(DUST, "§fFossil Dust")
                .extra(ExcavatorProfitTracker::perRun);
        hud = HudEditElement.of("excavatorTracker", "Fossil Excavation Tracker").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().excavatorTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("ExcavatorProfitTracker").section("Mining").when(() -> cfg().excavatorTracker || cfg().excavatorPowderStack)
                .tick(ExcavatorProfitTracker::tick)
                .serverChat(ExcavatorProfitTracker::onChat)
                .hud("excavatortracker", ExcavatorProfitTracker::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Fossil Excavation");
                    b.toggle("Fossil Excavation Profit Tracker", () -> c.excavatorTracker, v -> c.excavatorTracker = v)
                            .desc("Every Fossil Excavator reward against the Suspicious Scrap spent, with profit per excavation. Shown at the Fossil Research Center (/tmap gui).")
                            .tags("fossil", "excavator", "scrap", "profit", "tracker", "glacite");
                    b.toggle("  Profit Chat Message", () -> c.excavatorChat, v -> c.excavatorChat = v)
                            .desc("\"Profit this excavation\" after each dig, hover for the loot");
                    b.toggle("  Count Fossil Dust", () -> c.excavatorFossilDust, v -> c.excavatorFossilDust = v)
                            .desc("Priced at 1/500 of a Suspicious Scrap");
                    b.toggle("  Count Glacite Powder", () -> c.excavatorPowder, v -> c.excavatorPowder = v);
                    b.priceSource("  Price Source", () -> c.excavatorPriceSource, v -> c.excavatorPriceSource = v);
                    b.trackerButtons(tracker);
                    b.toggle("Glacite Powder Stack Number", () -> c.excavatorPowderStack, v -> c.excavatorPowderStack = v)
                            .desc("Fossil Excavator menu: the Glacite Powder amount as a short number on the slot");
                });
    }

    private static double price(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().excavatorPriceSource);
        return p == null ? 0 : p;
    }

    private static String perRun() {
        long runs = tracker.count(RUNS, tracker.view());
        if (runs <= 0) return null;
        double per = tracker.profit(tracker.view()) / runs;
        return "§7Profit per excavation: " + (per < 0 ? "§c-" : "§6") + ItemPrice.format(Math.abs(per));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Fossil Excavator")) {
            if (++ticks % 5 == 0 || cs != menu) readMenu(cs);
        } else {
            if (menu != null) java.util.Arrays.fill(slotText, null);
            menu = null;
            inMenu = false;
            ticks++;
        }
        if (!cfg().excavatorTracker) return;
        if (ticks % 20 == 0) {
            inCenter = false;
            if (SkyblockUtils.getArea().equalsIgnoreCase("Dwarven Mines")) {
                for (String l : ScoreboardUtils.getScoreboardLines()) {
                    if (l.contains("Fossil Research")) { inCenter = true; break; }
                }
            }
        }
        double scrap = price(SCRAP);
        tracker.fixedValue(USED, -scrap);
        tracker.fixedValue(DUST, scrap / 500.0);
        tracker.tick();
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        menu = cs;
        java.util.Arrays.fill(slotText, null);
        boolean start = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index >= slotText.length) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (name.equals("Start Excavator")) start = true;
            if (!cfg().excavatorPowderStack) continue;
            Matcher m = POWDER_SLOT.matcher(name);
            if (m.matches()) slotText[slot.index] = "§b" + ItemPrice.format(Long.parseLong(m.group(1).replace(",", "")));
        }
        inMenu = start;
    }

    /** Slot text hook (LoreSlotTextMixin). */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (menu == null || slot.index >= slotText.length || slot.container instanceof Inventory) return;
        String t = slotText[slot.index];
        if (t == null || Minecraft.getInstance().gui.screen() != menu) return;
        var font = Minecraft.getInstance().font;
        ctx.text(font, t, slot.x + 17 - font.width(t), slot.y + 9, 0xFFFFFFFF, true);
    }

    private static void onChat(String text) {
        if (!cfg().excavatorTracker || !SkyblockUtils.getArea().equalsIgnoreCase("Dwarven Mines")) return;
        String plain = StringUtil.strip(text);
        String t = plain.trim();
        if (t.equals(EMPTY)) {
            loot.clear();
            finish();
            return;
        }
        if (t.equals(START)) {
            inLoot = true;
            loot.clear();
            return;
        }
        if (!inLoot) return;
        if (END.matcher(t).matches()) {
            inLoot = false;
            finish();
            return;
        }
        if (!plain.startsWith("    ") || t.isEmpty()) return;
        Matcher m = AMOUNT.matcher(t);
        if (!m.matches()) return;
        long amount = 1;
        String n = m.group(1) != null ? m.group(1) : m.group(3);
        if (n != null) {
            try { amount = Long.parseLong(n.replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        String name = m.group(2).trim();
        String key;
        if (name.equals("Glacite Powder")) {
            if (!cfg().excavatorPowder) return;
            key = POWDER;
        } else if (name.equals("Fossil Dust")) {
            if (!cfg().excavatorFossilDust) return;
            key = DUST;
        } else {
            key = PriceManager.idForName(name);
            if (key == null) key = SkyblockItems.idForName(name);
            if (key == null) return;
        }
        loot.merge(key, amount, Long::sum);
    }

    private static void finish() {
        tracker.add(RUNS, 1);
        tracker.add(USED, 1);
        double total = 0;
        List<Map.Entry<String, Double>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : loot.entrySet()) {
            tracker.add(e.getKey(), e.getValue());
            if (e.getKey().equals(POWDER)) continue;
            double v = tracker.value(e.getKey(), e.getValue());
            total += v;
            rows.add(Map.entry(e.getKey(), v));
        }
        double scrap = price(SCRAP);
        total -= scrap;
        Minecraft mc = Minecraft.getInstance();
        if (cfg().excavatorChat && mc.player != null) {
            rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<String> hover = new ArrayList<>();
            for (Map.Entry<String, Double> r : rows) {
                String name = r.getKey().equals(DUST) ? "§fFossil Dust" : SkyblockItems.coloredName(r.getKey());
                hover.add("§eFound " + (name != null ? name : r.getKey()) + " §8" + loot.get(r.getKey()) + "x §7(§6"
                        + ItemPrice.format(r.getValue()) + "§7)");
            }
            hover.add("§9Suspicious Scrap§7: §c-" + ItemPrice.format(scrap));
            String msg = "Profit this excavation: " + (total < 0 ? "§c-" : "§6") + ItemPrice.format(Math.abs(total));
            hover.add("");
            hover.add("§e" + msg);
            Component tip = Component.literal(String.join("\n", hover));
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §e" + msg)
                    .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(tip))));
        }
        loot.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().excavatorTracker || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> ? !inMenu : !inCenter) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
