package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Editor for the leap menu "Custom" sort order: a top-to-bottom list of player names. Used when the
 * leap Sort Mode is set to "Custom". Names are matched case-insensitively. Opened from /tmap -> Leap.
 */
public class LeapOrderScreen extends Screen {
    private static final int PANEL_W = 320, PANEL_H = 280;
    private static final int ROW_H = 24, VISIBLE = 7;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;

    public LeapOrderScreen() { super(Component.literal("Leap Custom Order")); }

    private List<String> list() { return TeslaMapsConfig.get().leapCustomOrder; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 50;
        listW = PANEL_W - 32;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        List<String> b = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            int ry = listY + row * ROW_H;

            EditBox name = new EditBox(this.font, listX + 16, ry, listW - 16 - 44, 20, Component.literal("name"));
            name.setMaxLength(16);
            name.setHint(Component.literal("PlayerName"));
            name.setValue(b.get(idx));
            name.setResponder(v -> b.set(idx, v.toLowerCase()));
            addRenderableWidget(name);

            // up
            addRenderableWidget(Button.builder(Component.literal("▲"), btn -> { if (idx > 0) { var t = b.remove(idx); b.add(idx - 1, t); rebuild(); } })
                    .bounds(listX + listW - 42, ry, 18, 20).build());
            // remove
            addRenderableWidget(Button.builder(Component.literal("x"), btn -> { b.remove(idx); rebuild(); })
                    .bounds(listX + listW - 20, ry, 20, 20).build());
            // index label drawn in render
        }

        addRenderableWidget(Button.builder(Component.literal("+ Add"), btn -> {
            b.add("");
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
        }).bounds(panelX + 16, panelY + PANEL_H - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 30, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(list().size() - VISIBLE, scroll - (int) Math.signum(sy)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, AppleColors.CARD_BACKGROUND);
        ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_BLUE);

        ctx.text(this.font, "§b⇅ §fLeap Custom Order", panelX + 16, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, "§7Top = first slot. Used when Sort Mode = Custom.", panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

        // row index numbers
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list().size()) break;
            ctx.text(this.font, "§8" + (idx + 1), listX, listY + row * ROW_H + 6, AppleColors.TEXT_TERTIARY);
        }
        if (list().isEmpty()) {
            ctx.text(this.font, "§7No names — click \"+ Add\".", listX, listY, AppleColors.TEXT_TERTIARY);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        list().removeIf(s -> s == null || s.isBlank());
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
