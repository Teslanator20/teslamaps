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

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class KeybindMessageScreen extends Screen {
    private static final int PANEL_W = 400, PANEL_H = 270;
    private static final int ROW_H = 26, VISIBLE = 6, KEY_W = 92;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;
    private int listening = -1; // index of the keybind currently waiting for a key press

    public KeybindMessageScreen() { super(Component.literal("Keybind Messages")); }

    private List<TeslaMapsConfig.Keybind> binds() { return TeslaMapsConfig.get().keybinds; }

    @Override
    protected void init() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (binds().isEmpty() && c.keybindChatMessage != null && !c.keybindChatMessage.isBlank()) {
            binds().add(new TeslaMapsConfig.Keybind(-1, c.keybindChatMessage));
            c.keybindChatMessage = "";
        }
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 50;
        listW = PANEL_W - 32;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        List<TeslaMapsConfig.Keybind> b = binds();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            TeslaMapsConfig.Keybind kb = b.get(idx);
            int ry = listY + row * ROW_H;

            String label = listening == idx ? "press..." : keyName(kb.key);
            addRenderableWidget(Button.builder(Component.literal(label), btn -> { listening = idx; rebuild(); })
                    .bounds(listX, ry, KEY_W, 20).build());

            EditBox box = new EditBox(this.font, listX + KEY_W + 6, ry, listW - KEY_W - 6 - 24, 20, Component.literal("Message"));
            box.setMaxLength(256);
            box.setHint(Component.literal("/pc gg  ·  good luck"));
            box.setValue(kb.message == null ? "" : kb.message);
            box.setResponder(v -> kb.message = v);
            addRenderableWidget(box);

            addRenderableWidget(Button.builder(Component.literal("x"), btn -> { b.remove(idx); listening = -1; rebuild(); })
                    .bounds(listX + listW - 20, ry, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("+ Add Hotkey"), btn -> {
            b.add(new TeslaMapsConfig.Keybind(-1, ""));
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
        }).bounds(panelX + 16, panelY + PANEL_H - 52, 130, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 28, 100, 20).build());
    }

    private String keyName(int key) {
        if (key < 0) return "Unbound";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening >= 0) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE && listening < binds().size()) {
                binds().get(listening).key = event.key();
            }
            listening = -1; // ESC just cancels
            rebuild();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (binds().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(binds().size() - VISIBLE, scroll - (int) Math.signum(sy)));
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

        ctx.text(this.font, "§b⌨ §fHotkeys  Chat Messages", panelX + 16, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, "Click a key button, press a key, type a message. / = command.", panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

        if (binds().isEmpty()) {
            ctx.text(this.font, "§7No hotkeys yet — click \"+ Add Hotkey\".", panelX + 16, panelY + 56, AppleColors.TEXT_TERTIARY);
        }
        if (binds().size() > VISIBLE) {
            ctx.text(this.font, "§8" + binds().size() + " total · scroll", panelX + PANEL_W - 110, panelY + PANEL_H - 48, AppleColors.TEXT_TERTIARY);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    // squared (matches the rest of the mod's sharp style); border added for definition
    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x, y, x + w, y + h, color);
        ctx.fill(x, y, x + w, y + 1, AppleColors.INPUT_BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, AppleColors.INPUT_BORDER);
        ctx.fill(x, y, x + 1, y + h, AppleColors.INPUT_BORDER);
        ctx.fill(x + w - 1, y, x + w, y + h, AppleColors.INPUT_BORDER);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
