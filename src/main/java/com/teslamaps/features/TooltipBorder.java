package com.teslamaps.features;

// NEU's TooltipTweaks "Tooltip Border Colours" / "Tooltip Border Opacity"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class TooltipBorder {

    private static Feature feature;
    private static int pending = -1;
    private static Screen pendingScreen;

    private TooltipBorder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TooltipBorder").when(() -> cfg().rarityTooltipBorder)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Tooltip Border");
                    b.toggle("Rarity Tooltip Border", () -> c.rarityTooltipBorder, v -> c.rarityTooltipBorder = v)
                            .desc("Draws the tooltip frame in the rarity colour of the hovered item.");
                    b.slider("  Border Opacity", 0f, 1f, () -> c.rarityTooltipBorderOpacity, v -> c.rarityTooltipBorderOpacity = v, 2);
                });
    }

    public static void prepare(Screen screen, Slot hovered) {
        pending = -1;
        pendingScreen = screen;
        if (feature == null || !feature.active() || hovered == null) return;
        ItemStack stack = hovered.getItem();
        if (stack.isEmpty()) return;
        pending = RarityBackground.rarityColor(stack);
    }

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        if (pending == -1 || Minecraft.getInstance().gui.screen() != pendingScreen) return;
        if (feature == null || !feature.active()) return;
        int a = Math.round(Math.max(0f, Math.min(1f, cfg().rarityTooltipBorderOpacity)) * 255);
        if (a == 0) return;
        int col = (a << 24) | (pending & 0xFFFFFF);
        int x0 = x - 3, y0 = y - 3, x1 = x + w + 3, y1 = y + h + 3;
        ctx.fill(x0, y0, x1, y0 + 1, col);
        ctx.fill(x0, y1 - 1, x1, y1, col);
        ctx.fill(x0, y0 + 1, x0 + 1, y1 - 1, col);
        ctx.fill(x1 - 1, y0 + 1, x1, y1 - 1, col);
    }
}
