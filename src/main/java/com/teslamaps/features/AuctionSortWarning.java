package com.teslamaps.features;

// Ported from NEU's AuctionSortModeWarning.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class AuctionSortWarning {

    private static String warning;
    private static int warningWidth;
    private static Object shownOn;
    private static int ticks;
    private static Feature feature;

    private AuctionSortWarning() {}

    public static void register() {
        feature = Feature.of("AuctionSortWarning").section("Inventory")
                .when(() -> TeslaMapsConfig.get().auctionSortWarning)
                .tick(AuctionSortWarning::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Auction Sort Warning", () -> c.auctionSortWarning, v -> c.auctionSortWarning = v)
                            .desc("Warning above the auction browser while it is not sorted by Lowest Price.")
                            .tags("auction", "sort", "lowest price");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            warning = null;
            return;
        }
        if (ticks++ % 5 != 0 && shownOn == cs) return;
        shownOn = cs;
        warning = null;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.startsWith("Auctions Browser") && !title.startsWith("Auctions: \"")) return;
        var slots = cs.getMenu().slots;
        if (slots.size() <= 50) return;
        String selected = null;
        for (String l : ItemUtil.loreLines(slots.get(50).getItem())) {
            String t = l.trim();
            if (t.startsWith("▶ ")) selected = t.substring(2).trim();
        }
        if (selected == null || selected.equals("Lowest Price")) return;
        warning = "§aSort: " + (selected.equals("Highest Price") ? "§c" : "§e") + selected;
        warningWidth = mc.font.width(warning);
    }

    public static void render(GuiGraphicsExtractor g) {
        String w = warning;
        if (w == null || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != shownOn || !(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int cx = acc.getX() + acc.getImageWidth() / 2 + 9;
        int cy = acc.getY() + 26;
        HudStyle.panel(g, cx - warningWidth / 2 - 4, cy - 6, warningWidth + 8, 12);
        g.text(mc.font, w, cx - warningWidth / 2, cy - 4, 0xFFFFFFFF, true);
    }
}
