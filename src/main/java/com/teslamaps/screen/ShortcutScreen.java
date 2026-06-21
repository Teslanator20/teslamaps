/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

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
        ctx.text(this.font, "alias  command (no slashes). §eRelog to apply changes.", panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

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
