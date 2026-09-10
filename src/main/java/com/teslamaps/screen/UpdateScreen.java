package com.teslamaps.screen;

import com.teslamaps.features.Updater;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UpdateScreen extends Screen {
    private static final int PANEL_W = 300, PANEL_H = 120;

    private final Screen parent;
    private int panelX, panelY;
    private Updater.State builtFor;

    public UpdateScreen(Screen parent) {
        super(Component.literal("TeslaMaps Update"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        clearWidgets();
        builtFor = Updater.state;
        int y = panelY + PANEL_H - 34;
        if (Updater.state == Updater.State.AVAILABLE) {
            addRenderableWidget(new TeslaButton(panelX + 16, y, 120, 22, "§aDownload", () -> {
                Updater.install();
                rebuild();
            }));
            addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, y, 100, 22, "§7Later", this::onClose));
        } else {
            addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, y, 100, 22, "§fClose", this::onClose));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§b§lTeslaMaps §fUpdate", panelX + 16, panelY + 16, 0xFFFFFFFF);
        String line = switch (Updater.state) {
            case AVAILABLE -> "§e" + Updater.currentVersion() + " §7-> §a" + Updater.latestVersion;
            case DOWNLOADING -> "§7Downloading §e" + Updater.latestVersion + "§7...";
            case READY -> "§aInstalled — restart the game.";
            case FAILED -> "§cFailed: §7" + Updater.error;
            default -> "§7No update pending.";
        };
        ctx.text(this.font, line, panelX + 16, panelY + 40, 0xFFFFFFFF);
        if (Updater.state == Updater.State.AVAILABLE) {
            ctx.text(this.font, "§8Downloads the new jar into mods/, swap happens on restart.",
                    panelX + 16, panelY + 56, 0xFFFFFFFF);
        }

        // buttons follow the state (download -> downloading -> ready)
        if (Updater.state != builtFor) rebuild();

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
