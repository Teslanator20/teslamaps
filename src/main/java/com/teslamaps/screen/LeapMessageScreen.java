package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LeapMessageScreen extends Screen {
    private static final int PANEL_W = 340, PANEL_H = 130;

    private final Screen parent;
    private int panelX, panelY;

    public LeapMessageScreen(Screen parent) {
        super(Component.literal("Leap Announce"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        field(0, () -> c.leapAnnounceText, v -> c.leapAnnounceText = v);
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private void field(int row, Supplier<String> get, Consumer<String> set) {
        EditBox box = new EditBox(this.font, panelX + 124, rowY(row) + 7, 196, 12, Component.literal("text"));
        box.setMaxLength(100);
        box.setBordered(false);
        box.setValue(get.get() == null ? "" : get.get());
        box.setResponder(set);
        addRenderableWidget(box);
    }

    private int rowY(int row) {
        return panelY + 44 + row * 30;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(this.font, "§f§lLeap Announce", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8sent to party chat, {name} = who you leaped to", panelX + 16, panelY + 28, 0xFFFFFFFF);
        row(ctx, 0, "Message");
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor ctx, int row, String label) {
        int y = rowY(row);
        ctx.text(this.font, "§f" + label, panelX + 16, y + 7, 0xFFFFFFFF);
        HudStyle.screenButton(ctx, panelX + 120, y, 204, 22, false, true);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
