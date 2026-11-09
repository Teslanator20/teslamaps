package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LividMessageScreen extends Screen {
    private static final int PANEL_W = 340, PANEL_H = 130;

    private final Screen parent;
    private int panelX, panelY;

    public LividMessageScreen(Screen parent) {
        super(Component.literal("Livid Message"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        EditBox box = new EditBox(this.font, panelX + 124, panelY + 51, 196, 12, Component.literal("text"));
        box.setMaxLength(100);
        box.setBordered(false);
        box.setValue(c.lividAnnounceText == null ? "" : c.lividAnnounceText);
        box.setResponder(v -> c.lividAnnounceText = v);
        addRenderableWidget(box);
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(this.font, "§f§lLivid Announce", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8sent to party chat, [color] = the Livid", panelX + 16, panelY + 28, 0xFFFFFFFF);
        ctx.text(this.font, "§fMessage", panelX + 16, panelY + 51, 0xFFFFFFFF);
        HudStyle.screenButton(ctx, panelX + 120, panelY + 44, 204, 22, false, true);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
