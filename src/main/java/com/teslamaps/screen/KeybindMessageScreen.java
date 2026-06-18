package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.KeybindMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * A small polished screen to configure the keybind chat message (the message sent by the
 * "Send Chat Message" keybind). Opened with /tmap msg.
 */
public class KeybindMessageScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 190;
    private static final int PAD = 24;

    private EditBox messageBox;
    private int panelX, panelY;

    public KeybindMessageScreen() {
        super(Component.literal("Keybind Message"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int inputX = panelX + PAD;
        int inputW = PANEL_W - PAD * 2;
        int inputY = panelY + 74;

        messageBox = new EditBox(this.font, inputX + 7, inputY + 9, inputW - 14, 12, Component.literal("Message"));
        messageBox.setMaxLength(256);
        messageBox.setBordered(false);
        messageBox.setHint(Component.literal("e.g. /pc gg  ·  good luck!"));
        messageBox.setValue(TeslaMapsConfig.get().keybindChatMessage);
        addRenderableWidget(messageBox);
        setInitialFocus(messageBox);

        // Rebind button on the keybind row
        addRenderableWidget(Button.builder(Component.literal("Rebind"), b -> {
            save();
            this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options));
        }).bounds(panelX + PANEL_W - PAD - 76, panelY + 120, 76, 18).build());

        // Done
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + PANEL_W / 2 - 60, panelY + PANEL_H - 30, 120, 20).build());
    }

    private void save() {
        if (messageBox != null) {
            TeslaMapsConfig.get().keybindChatMessage = messageBox.getValue();
            TeslaMapsConfig.save();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC0000000);

        // card (shadow + body + accent bar)
        roundRect(ctx, panelX + 3, panelY + 4, PANEL_W, PANEL_H, 10, AppleColors.CARD_SHADOW);
        roundRect(ctx, panelX, panelY, PANEL_W, PANEL_H, 10, AppleColors.CARD_BACKGROUND);
        ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_BLUE);

        // header
        ctx.text(this.font, "§b⌨ §fKeybind → Chat Message", panelX + PAD, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, "Press your key in-game to send this message.", panelX + PAD, panelY + 30, AppleColors.TEXT_SECONDARY);

        // input field
        int inputX = panelX + PAD, inputW = PANEL_W - PAD * 2, inputY = panelY + 74;
        ctx.text(this.font, "MESSAGE", panelX + PAD, panelY + 58, AppleColors.TEXT_TERTIARY);
        roundRect(ctx, inputX, inputY, inputW, 30, 6, AppleColors.INPUT_BACKGROUND);
        outline(ctx, inputX, inputY, inputW, 30, messageBox != null && messageBox.isFocused()
                ? AppleColors.INPUT_FOCUSED : AppleColors.INPUT_BORDER);

        ctx.text(this.font, "§7Start with §f/§7 to send as a command", panelX + PAD, panelY + 110, AppleColors.TEXT_TERTIARY);

        // keybind row
        KeyMapping key = KeybindMessage.getKey();
        boolean unbound = key == null || key.isUnbound();
        String keyText = unbound ? "§cUnbound" : "§f" + key.getTranslatedKeyMessage().getString();
        ctx.text(this.font, "§7Key:", panelX + PAD, panelY + 124, AppleColors.TEXT_SECONDARY);
        ctx.text(this.font, keyText, panelX + PAD + this.font.width("Key:  "), panelY + 124, AppleColors.TEXT_PRIMARY);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        ctx.fill(x + 1, y + 1, x + r, y + r, color);
        ctx.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        ctx.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }

    private void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
