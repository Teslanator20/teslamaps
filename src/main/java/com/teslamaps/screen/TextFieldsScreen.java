package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small popup with one labelled text box per row. */
public class TextFieldsScreen extends Screen {
    public record Field(String label, Supplier<String> get, Consumer<String> set) {}

    private static final int PANEL_W = 360;

    private final Screen parent;
    private final String heading, hint;
    private final List<Field> fields;
    private int panelX, panelY, panelH;
    private int maxLength = 100;

    public TextFieldsScreen(Screen parent, String heading, String hint, List<Field> fields, int maxLength) {
        this(parent, heading, hint, fields);
        this.maxLength = maxLength;
    }

    public TextFieldsScreen(Screen parent, String heading, String hint, List<Field> fields) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.hint = hint;
        this.fields = fields;
    }

    @Override
    protected void init() {
        panelH = 44 + fields.size() * 30 + 44;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            EditBox box = new EditBox(this.font, panelX + 144, rowY(i) + 7, 196, 12, Component.literal("text"));
            box.setMaxLength(maxLength);
            box.setBordered(false);
            box.setValue(f.get().get() == null ? "" : f.get().get());
            box.setResponder(f.set());
            addRenderableWidget(box);
        }
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + panelH - 34, 100, 22,
                "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private int rowY(int row) {
        return panelY + 44 + row * 30;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);
        ctx.text(this.font, "§f§l" + heading, panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8" + hint, panelX + 16, panelY + 28, 0xFFFFFFFF);
        for (int i = 0; i < fields.size(); i++) {
            int y = rowY(i);
            ctx.text(this.font, "§f" + fields.get(i).label(), panelX + 16, y + 7, 0xFFFFFFFF);
            HudStyle.screenButton(ctx, panelX + 140, y, 204, 22, false, true);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
