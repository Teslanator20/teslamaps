package com.teslamaps.features;

// After SkyHanni's GardenPlotMenuHighlighting.kt and GardenNextPlotPrice.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GardenPlotMenu {

    private static final int NONE = 0, CURRENT = 1, PESTS = 2, SPRAY = 3, LOCKED = 4, PASTING = 5;
    private static final int[] COLORS = {0, 0x9055FF55, 0x90FF5555, 0x90FFAA00, 0x90303030, 0x90FFFF55};

    private static final Pattern SPRAYED = Pattern.compile("SPRAYONATOR! You sprayed Plot - (.+) with (?:\\d+ )?(.+)!");
    private static final Pattern RAN_OUT = Pattern.compile("SPRAYONATOR! The smell of .+ on Plot - (.+) ran out!");
    private static final Pattern PLOT_PESTS = Pattern.compile("(\\d+) Pests?\\b");
    private static final Pattern AMOUNT = Pattern.compile("^(?:([\\d,]+)x )?(.+?)(?: x([\\d,]+))?$");

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inMenu;
    private static int ticks;
    private static final int[] status = new int[GardenPlots.PLOT_COUNT];
    private static final String[] counts = new String[GardenPlots.PLOT_COUNT];
    private static final Map<String, Long> sprayEnd = new HashMap<>();

    private GardenPlotMenu() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GardenPlotMenu").section("Garden")
                .when(() -> cfg().gardenPlotMenuHighlight || cfg().gardenPlotMenuPrice)
                .tick(GardenPlotMenu::tick)
                .serverChat(GardenPlotMenu::onChat)
                .config("Garden", b -> {
                    b.label("Configure Plots Menu");
                    b.toggle("Plot Status Colors", () -> cfg().gardenPlotMenuHighlight, v -> cfg().gardenPlotMenuHighlight = v)
                            .desc("Colours the plots in /desk > Configure Plots: current green, pests red (number = pests), spray gold (number = minutes left), locked grey, pasting yellow.")
                            .tags("plot", "desk", "spray", "pest", "locked");
                    b.toggle("Next Plot Price", () -> cfg().gardenPlotMenuPrice, v -> cfg().gardenPlotMenuPrice = v)
                            .desc("Coin value of the unlock cost in the tooltip of a locked plot.");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!inMenu || !cfg().gardenPlotMenuPrice || feature == null || !feature.active()) return;
            addPrice(stack, lines);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            inMenu = mc.screen instanceof ContainerScreen cs && SkyblockUtils.isInGarden()
                    && StringUtil.stripTrim(cs.getTitle().getString()).equals("Configure Plots");
            ticks = 0;
        }
        if (ticks++ % 20 == 0 && SkyblockUtils.isInGarden()) syncSpray();
        if (inMenu && ticks % 10 == 1 && mc.screen instanceof ContainerScreen cs) scan(cs);
    }

    private static void syncSpray() {
        String here = PestData.currentPlot();
        String spray = PestData.spray();
        if (here == null || spray == null) return;
        long end = System.currentTimeMillis() + PestData.spraySeconds() * 1000L;
        Long known = sprayEnd.get(here);
        if (known == null || Math.abs(known - end) > 60_000L) sprayEnd.put(here, end);
    }

    private static void scan(ContainerScreen cs) {
        List<String> infested = PestData.infestedPlots();
        String here = PestData.currentPlot();
        long now = System.currentTimeMillis();
        for (int i = 0; i < GardenPlots.PLOT_COUNT; i++) {
            status[i] = NONE;
            counts[i] = null;
            if (i == GardenPlots.BARN_SLOT) continue;
            int slot = (i / 5) * 9 + 2 + i % 5;
            if (slot >= cs.getMenu().slots.size()) continue;
            ItemStack stack = cs.getMenu().slots.get(slot).getItem();
            if (stack.isEmpty()) continue;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            int dash = name.indexOf(" - ");
            if (!name.startsWith("Plot") || dash < 0) continue;
            name = name.substring(dash + 3).trim();
            List<String> lore = ItemUtil.loreLines(stack);
            boolean locked = false, pasting = false;
            int pests = 0;
            for (String l : lore) {
                if (l.contains("Cost:")) locked = true;
                if (l.contains("Pasting in progress:")) pasting = true;
                if (l.contains("Pest")) {
                    Matcher m = PLOT_PESTS.matcher(l);
                    if (m.find()) pests = Integer.parseInt(m.group(1));
                }
            }
            if (pests == 0 && containsIgnoreCase(infested, name)) pests = 1;
            Long end = sprayEnd.get(name);
            int sprayMin = end != null && end > now ? (int) Math.ceil((end - now) / 60_000.0) : 0;
            if (here != null && here.equalsIgnoreCase(name)) status[i] = CURRENT;
            else if (pests > 0) {
                status[i] = PESTS;
                counts[i] = Integer.toString(pests);
            } else if (sprayMin > 0) {
                status[i] = SPRAY;
                counts[i] = Integer.toString(sprayMin);
            } else if (locked) status[i] = LOCKED;
            else if (pasting) status[i] = PASTING;
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String l : list) if (l.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static int plotIndex(Slot slot) {
        if (slot.container instanceof Inventory) return -1;
        int i = slot.index;
        int row = i / 9, col = i % 9;
        if (row > 4 || col < 2 || col > 6) return -1;
        int idx = row * 5 + col - 2;
        return idx == GardenPlots.BARN_SLOT ? -1 : idx;
    }

    private static boolean live() {
        return inMenu && Minecraft.getInstance().screen == lastScreen && feature != null && feature.active();
    }

    /** Behind the item, from SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor g, Slot slot) {
        if (!inMenu || !cfg().gardenPlotMenuHighlight || !live()) return;
        int i = plotIndex(slot);
        if (i < 0 || status[i] == NONE) return;
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, COLORS[status[i]]);
    }

    /** Pest count / spray minutes over the item, from LoreSlotTextMixin. */
    public static void drawCount(GuiGraphicsExtractor g, Slot slot) {
        if (!inMenu || !cfg().gardenPlotMenuHighlight || !live()) return;
        int i = plotIndex(slot);
        if (i < 0 || counts[i] == null) return;
        var font = Minecraft.getInstance().font;
        g.text(font, counts[i], slot.x + 17 - font.width(counts[i]), slot.y + 9, 0xFFFFFFFF, true);
    }

    private static void addPrice(ItemStack stack, List<Component> lines) {
        if (!StringUtil.stripTrim(stack.getHoverName().getString()).startsWith("Plot")) return;
        for (int i = 1; i < lines.size() - 1; i++) {
            if (!lines.get(i).getString().contains("Cost")) continue;
            String next = StringUtil.stripTrim(lines.get(i + 1).getString());
            Matcher m = AMOUNT.matcher(next);
            if (!m.matches()) return;
            String amountText = m.group(1) != null ? m.group(1) : m.group(3);
            long amount = amountText == null ? 1 : Long.parseLong(amountText.replace(",", ""));
            double price = PriceManager.getPrice(PriceManager.idForName(m.group(2)));
            if (price <= 0) return;
            lines.set(i + 1, lines.get(i + 1).copy()
                    .append(Component.literal(" §7(§6" + EstimatedValue.fmt(price * amount) + "§7)")));
            return;
        }
    }

    private static void onChat(String raw) {
        if (!cfg().gardenPlotMenuHighlight) return;
        String text = StringUtil.stripTrim(raw);
        if (!text.startsWith("SPRAYONATOR!") && !text.startsWith("SPLASH!")) return;
        if (text.equals("SPLASH! Your Garden was cleared of all active Sprayonator effects!")) {
            sprayEnd.clear();
            return;
        }
        Matcher m = SPRAYED.matcher(text);
        if (m.matches()) {
            sprayEnd.put(m.group(1).trim(), System.currentTimeMillis() + sprayMinutes() * 60_000L);
            return;
        }
        m = RAN_OUT.matcher(text);
        if (m.matches()) sprayEnd.remove(m.group(1).trim());
    }

    private static int sprayMinutes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 30;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        return switch (id) {
            case "JUICY_SPRAYONATOR" -> 45;
            case "SALTY_SPRAYONATOR" -> 60;
            default -> 30;
        };
    }
}
