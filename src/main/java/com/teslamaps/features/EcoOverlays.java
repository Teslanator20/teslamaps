package com.teslamaps.features;

import com.teslamaps.debug.Perf;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/** Container hooks of the w12 economy panels, one call site each in the shared mixins. */
public final class EcoOverlays {

    private EcoOverlays() {}

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        Perf.begin("EcoOverlays.render");
        try {
            AnitaShop.render(g, mx, my);
            BazaarHelpers.render(g, mx, my);
            CraftMaterials.render(g, mx, my);
            AuctionSortWarning.render(g);
        } finally {
            Perf.end();
        }
    }

    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        InstaSellHighlight.drawSlot(g, slot);
    }

    public static boolean click(double mx, double my, int button) {
        return CraftMaterials.click(mx, my, button);
    }

    public static boolean scroll(double mx, double my, double dy) {
        return CraftMaterials.scroll(mx, my, dy) || AnitaShop.scroll(mx, my, dy);
    }

    public static boolean key(int key, Slot hovered) {
        return PriceHistory.onKey(key, hovered) || CraftMaterials.keyPressed(key);
    }

    public static boolean charTyped(int cp) {
        return CraftMaterials.charTyped(cp);
    }
}
