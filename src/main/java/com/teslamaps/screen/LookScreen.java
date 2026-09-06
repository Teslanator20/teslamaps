package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.MenuBackgroundRenderer;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu background picker reached from the eye on the main menu. The background
 * renders behind it, so every change is its own preview. Skybox settings live in
 * the full settings screen — this one is about the menu you are looking at.
 * <p>
 * Layout is computed once per {@code init} into fields, and both the widgets and
 * the labels read the same numbers — that is the only way the two stay lined up.
 */
public class LookScreen extends Screen {

    private final Screen parent;

    private static final int PANEL_W = 236;
    private static final int PAD = 12;
    private static final int ROW_H = 20;
    private static final int GAP = 4;
    private static final int TITLE_H = 26;
    private static final int ARROW_W = 20;
    private static final int ROWS = 6;

    private int panelX, panelY, panelH, contentX, contentW;

    public LookScreen(Screen parent) {
        super(Component.literal("Look"));
        this.parent = parent;
    }

    private static TeslaMapsConfig config() { return TeslaMapsConfig.get(); }

    private int rowY(int row) { return panelY + TITLE_H + row * (ROW_H + GAP); }

    @Override
    protected void init() {
        panelH = TITLE_H + ROWS * (ROW_H + GAP) - GAP + PAD;
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);
        contentX = panelX + PAD;
        contentW = PANEL_W - PAD * 2;

        // 0 — background, name sits between the arrows
        addRenderableWidget(new TeslaMenuButton(contentX, rowY(0), ARROW_W, ROW_H, Component.literal("<"),
                () -> { config().titleBackground = MenuBackgroundRenderer.cycle(config().titleBackground, -1); save(); }));
        addRenderableWidget(new TeslaMenuButton(contentX + contentW - ARROW_W, rowY(0), ARROW_W, ROW_H, Component.literal(">"),
                () -> { config().titleBackground = MenuBackgroundRenderer.cycle(config().titleBackground, 1); save(); }));

        // 1 — randomize
        addRenderableWidget(new TeslaMenuButton(contentX, rowY(1), contentW, ROW_H, Component.literal("Randomize"),
                () -> {
                    String pick = MenuBackgroundRenderer.random(config().titleBackground);
                    config().titleBackground = pick;
                    // A tint multiplies the shader output; on a photo it would just muddy it,
                    // so images get a clean white and only the presets roll a colour.
                    config().titleBackgroundColor = com.teslamaps.render.gui.ImageBackgrounds.has(pick)
                            ? "FFFFFF"
                            : MenuBackgroundRenderer.randomTint();
                    save();
                }));

        // 2 — custom menu on/off
        addRenderableWidget(new TeslaMenuButton(contentX, rowY(2), contentW, ROW_H,
                Component.literal("Custom Menu " + onOff(config().customTitleScreen)),
                () -> { config().customTitleScreen = !config().customTitleScreen; save(); rebuildWidgets(); }));

        // 3 — speed, value drawn between the buttons
        addRenderableWidget(new TeslaMenuButton(contentX, rowY(3), ARROW_W, ROW_H, Component.literal("-"),
                () -> { setSpeed(config().titleBackgroundSpeed - 0.1f); rebuildWidgets(); }));
        addRenderableWidget(new TeslaMenuButton(contentX + contentW - ARROW_W, rowY(3), ARROW_W, ROW_H, Component.literal("+"),
                () -> { setSpeed(config().titleBackgroundSpeed + 0.1f); rebuildWidgets(); }));

        addRenderableWidget(new TeslaMenuButton(contentX, rowY(4), contentW, ROW_H, Component.literal("All Settings"),
                () -> {
                    config().lastConfigCategory = "Menu & Skybox";
                    this.minecraft.setScreen(new MapConfigScreen());
                }));

        addRenderableWidget(new TeslaMenuButton(contentX, rowY(5), contentW, ROW_H, Component.literal("Done"), this::onClose));
    }

    private void setSpeed(float value) {
        config().titleBackgroundSpeed = Math.max(0.05f, Math.min(2.0f, Math.round(value * 100f) / 100f));
        save();
    }

    private static String onOff(boolean value) { return value ? "On" : "Off"; }

    private void save() { TeslaMapsConfig.save(); }

    /** Centres a label in the gap between the two arrow buttons, trimming if needed. */
    private void drawBetweenArrows(GuiGraphicsExtractor ctx, String text, int row, int color) {
        int available = contentW - (ARROW_W + GAP) * 2;
        String shown = text;
        while (this.font.width(shown) > available && shown.length() > 1) {
            shown = shown.substring(0, shown.length() - 1);
        }
        ctx.centeredText(this.font, shown, contentX + contentW / 2, rowY(row) + (ROW_H - 8) / 2, color);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        AnimClock.update();
        MenuBackgroundRenderer.render(ctx);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        TeslaGui.glassPanel(ctx, panelX, panelY, PANEL_W, panelH, 8, 26, 45, 70, 16);

        ctx.centeredText(this.font, "Look", panelX + PANEL_W / 2, panelY + 9, 0xFFF0F3FA);

        drawBetweenArrows(ctx, config().titleBackground, 0, 0xFFF0F3FA);
        drawBetweenArrows(ctx, String.format("Speed %.2f", config().titleBackgroundSpeed), 3, 0xFFAFC0D0);

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
