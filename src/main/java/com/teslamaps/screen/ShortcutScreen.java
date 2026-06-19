package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Edit command shortcuts ("/alias args" -> "/command args"), opened with /tmap shortcut.
 * Shortcuts are registered as client commands on join, so changes apply after a relog.
 */
public class ShortcutScreen extends Screen {
    private static final int PANEL_W = 420, PANEL_H = 280;
    private static final int ROW_H = 26, VISIBLE = 6, ALIAS_W = 80;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;

    public ShortcutScreen() { super(Component.literal("Command Shortcuts")); }

    private List<TeslaMapsConfig.Shortcut> list() { return TeslaMapsConfig.get().shortcuts; }

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
        List<TeslaMapsConfig.Shortcut> b = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            TeslaMapsConfig.Shortcut sc = b.get(idx);
            int ry = listY + row * ROW_H;

            EditBox alias = new EditBox(this.font, listX, ry, ALIAS_W, 20, Component.literal("alias"));
            alias.setMaxLength(32);
            alias.setHint(Component.literal("pk"));
            alias.setValue(sc.alias == null ? "" : sc.alias);
            alias.setResponder(v -> sc.alias = v);
            addRenderableWidget(alias);

            EditBox cmd = new EditBox(this.font, listX + ALIAS_W + 6, ry, listW - ALIAS_W - 6 - 24, 20, Component.literal("command"));
            cmd.setMaxLength(256);
            cmd.setHint(Component.literal("party kick"));
            cmd.setValue(sc.command == null ? "" : sc.command);
            cmd.setResponder(v -> sc.command = v);
            addRenderableWidget(cmd);

            addRenderableWidget(Button.builder(Component.literal("x"), btn -> { b.remove(idx); rebuild(); })
                    .bounds(listX + listW - 20, ry, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("+ Add Shortcut"), btn -> {
            b.add(new TeslaMapsConfig.Shortcut("", ""));
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
        }).bounds(panelX + 16, panelY + PANEL_H - 52, 130, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 28, 100, 20).build());
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
        roundRect(ctx, panelX, panelY, PANEL_W, PANEL_H, 10, AppleColors.CARD_BACKGROUND);
        ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_BLUE);

        ctx.text(this.font, "§b⌘ §fCommand Shortcuts", panelX + 16, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, "alias → command (no slashes). §eRelog to apply changes.", panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

        if (list().isEmpty()) {
            ctx.text(this.font, "§7No shortcuts — click \"+ Add Shortcut\".", panelX + 16, panelY + 56, AppleColors.TEXT_TERTIARY);
        }
        if (list().size() > VISIBLE) {
            ctx.text(this.font, "§8" + list().size() + " total · scroll", panelX + PANEL_W - 110, panelY + PANEL_H - 48, AppleColors.TEXT_TERTIARY);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
