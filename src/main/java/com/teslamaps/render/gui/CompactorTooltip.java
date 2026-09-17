package com.teslamaps.render.gui;

import com.teslamaps.features.CompactorPreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Grid layout ported from Skyblocker's CompactorPreviewTooltipComponent
public class CompactorTooltip implements ClientTooltipComponent {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier DISABLED_SLOT = Identifier.withDefaultNamespace("container/crafter/disabled_slot");

    private final CompactorPreview.Contents contents;
    private final int columns;

    public CompactorTooltip(CompactorPreview.Contents contents) {
        this.contents = contents;
        this.columns = Math.max(contents.cols(), 3);
    }

    private static final int STATUS_H = 12;

    @Override
    public int getHeight(Font font) {
        return contents.rows() * 18 + 24 + STATUS_H;
    }

    @Override
    public int getWidth(Font font) {
        return columns * 18 + 14;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
        g.text(font, "Power: " + (contents.enabled() ? "§aEnabled" : "§cDisabled"), x, y, 0xFFAAAAAA);
        y += STATUS_H;

        int rows = contents.rows();
        int cols = contents.cols();
        int bodyW = columns * 18 + 7;
        int bodyH = rows * 18 + 17;

        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, bodyW, bodyH, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + bodyW, y, 169, 0, 7, bodyH, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + bodyH, 0, 215, bodyW, 7, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + bodyW, y + bodyH, 169, 215, 7, 7, 256, 256);

        g.text(font, "Contents", x + 8, y + 6, 0xFF404040, false);

        // A single column sits in the middle, with the outer two slots crossed out
        int pad = cols == 1 ? 18 : 0;
        for (int i = 0; i < contents.slots().size(); i++) {
            int slotX = x + i % cols * 18 + 8 + pad;
            int slotY = y + i / cols * 18 + 18;

            if (cols == 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT, slotX - 19, slotY - 1, 18, 18);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT, slotX + 17, slotY - 1, 18, 18);
            }

            ItemStack item = contents.slots().get(i);
            if (item == null || item.isEmpty()) continue;
            g.item(item, slotX, slotY);
            g.itemDecorations(font, item, slotX, slotY);
        }
    }
}
