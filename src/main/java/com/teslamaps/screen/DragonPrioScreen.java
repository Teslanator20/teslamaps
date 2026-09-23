package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DragonPriority;
import com.teslamaps.render.gui.HudStyle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DragonPrioScreen extends Screen {
    private static final int PANEL_W = 300, PANEL_H = 210;

    private final Screen parent;
    private int panelX, panelY;

    public DragonPrioScreen(Screen parent) {
        super(Component.literal("Dragon Priority"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        field(0, () -> c.witherDragonSplitPrio, v -> c.witherDragonSplitPrio = v, 5);
        field(1, () -> c.witherDragonNoSplitPrio, v -> c.witherDragonNoSplitPrio = v, 5);
        field(2, () -> c.witherDragonBersTeam, v -> c.witherDragonBersTeam = v, 5);
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private void field(int row, Supplier<String> get, Consumer<String> set, int max) {
        EditBox box = new EditBox(this.font, panelX + 150, rowY(row) + 7, 80, 12, Component.literal("prio"));
        box.setMaxLength(max);
        box.setBordered(false);
        box.setValue(get.get() == null ? "" : get.get());
        box.setResponder(v -> set.accept(v.toLowerCase()));
        addRenderableWidget(box);
    }

    private int rowY(int row) {
        return panelY + 56 + row * 30;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(this.font, "§f§lDragon Priority", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8bers team <- first ... last -> arch team", panelX + 16, panelY + 30, 0xFFFFFFFF);

        TeslaMapsConfig c = TeslaMapsConfig.get();
        row(ctx, 0, "Split Priority", DragonPriority.validPrio(c.witherDragonSplitPrio));
        row(ctx, 1, "No Split Priority", DragonPriority.validPrio(c.witherDragonNoSplitPrio));
        row(ctx, 2, "Bers Team", DragonPriority.validTeam(c.witherDragonBersTeam));
        ctx.text(this.font, "§8letters r o g b p once each, team: a b h m t",
                panelX + 16, rowY(3) + 4, 0xFFFFFFFF);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor ctx, int row, String label, boolean valid) {
        int y = rowY(row);
        ctx.text(this.font, (valid ? "§f" : "§c") + label, panelX + 16, y + 7, 0xFFFFFFFF);
        HudStyle.screenButton(ctx, panelX + 146, y, 90, 22, false, true);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
