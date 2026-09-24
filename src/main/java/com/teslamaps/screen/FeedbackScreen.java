package com.teslamaps.screen;

import com.teslamaps.features.Feedback;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FeedbackScreen extends Screen {
    private static final int PANEL_W = 360, PANEL_H = 230;

    private final boolean bug;
    private String text;
    private String status = "";
    private boolean sending;
    private int panelX, panelY;
    private MultiLineEditBox box;
    private TeslaButton sendButton;

    public FeedbackScreen(boolean bug, String initial) {
        super(Component.literal(bug ? "Bug Report" : "Suggestion"));
        this.bug = bug;
        this.text = initial == null ? "" : initial;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        box = MultiLineEditBox.builder()
                .setX(panelX + 16).setY(panelY + 44)
                .setPlaceholder(Component.literal(bug
                        ? "What happened? What did you expect? How to reproduce?"
                        : "What should TeslaMaps do?"))
                .build(font, PANEL_W - 32, PANEL_H - 44 - 50, Component.literal("Message"));
        box.setCharacterLimit(Feedback.MAX_CHARS);
        box.setValue(text);
        box.setValueListener(v -> text = v);
        addRenderableWidget(box);
        setInitialFocus(box);

        sendButton = addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§aSend", this::send));
        sendButton.active = !sending;
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100 - 6 - 80, panelY + PANEL_H - 34, 80, 22,
                "§fCancel", this::onClose));

        ScreenFit.clamp(this);
    }

    private void send() {
        if (sending) return;
        if (text.isBlank()) {
            status = "§cWrite something first";
            return;
        }
        sending = true;
        sendButton.active = false;
        status = "§7Sending...";
        Feedback.send(bug, text.trim(), line -> {
            sending = false;
            if (line.startsWith("§a")) {
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("§7[TeslaMaps] " + line));
                if (minecraft.screen == this) onClose();
                return;
            }
            status = line;
            if (sendButton != null) sendButton.active = true;
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(font, bug ? "§f§lBug Report" : "§f§lSuggestion", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(font, "§8Sent with your name, mod version and current area.", panelX + 16, panelY + 28, 0xFFFFFFFF);
        ctx.text(font, "§8" + text.length() + "/" + Feedback.MAX_CHARS, panelX + 16, panelY + PANEL_H - 40, 0xFFFFFFFF);
        if (!status.isEmpty()) ctx.text(font, status, panelX + 16, panelY + PANEL_H - 28, 0xFFFFFFFF);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
