package com.teslamaps.screen;

import com.teslamaps.features.Updater;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UpdateScreen extends Screen {
    private static final int PANEL_W = 300, PAD = 16;
    private static final int TEXT_W = PANEL_W - 2 * PAD;
    private static final int LINE = 12;

    private final Screen parent;
    private int panelX, panelY, panelH;
    private Updater.State builtFor;
    private java.util.List<net.minecraft.util.FormattedCharSequence> body = java.util.List.of();

    public UpdateScreen(Screen parent) {
        super(Component.literal("TeslaMaps Update"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        clearWidgets();
        builtFor = Updater.state;
        buildBody();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        int y = panelY + panelH - 34;
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

    /** Wraps the body to the panel width, so a long line cannot run past the border. */
    private void buildBody() {
        String line = switch (Updater.state) {
            case AVAILABLE -> "§e" + Updater.currentVersion() + " §7-> §a" + Updater.latestVersion;
            case DOWNLOADING -> "§7Downloading §e" + Updater.latestVersion + "§7...";
            case READY -> "§aInstalled — restart the game.";
            case FAILED -> "§cFailed: §7" + Updater.error;
            default -> "§7No update pending.";
        };
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>(
                this.font.split(Component.literal(line), TEXT_W));
        if (Updater.state == Updater.State.AVAILABLE) {
            out.addAll(this.font.split(
                    Component.literal("§8Downloads the new jar into mods/, swap happens on restart."), TEXT_W));
        }
        body = out;
        panelH = 40 + body.size() * LINE + 50;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);

        ctx.text(this.font, "§b§lTeslaMaps §fUpdate", panelX + PAD, panelY + PAD, 0xFFFFFFFF);
        int y = panelY + 40;
        for (net.minecraft.util.FormattedCharSequence line : body) {
            ctx.text(this.font, line, panelX + PAD, y, 0xFFFFFFFF);
            y += LINE;
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
