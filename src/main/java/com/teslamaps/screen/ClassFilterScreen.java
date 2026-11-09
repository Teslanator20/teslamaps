package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ClassFilterScreen extends Screen {
    private static final int PANEL_W = 300, PANEL_H = 240;
    private static final int ROW_H = 26;
    private static final String[] CLASSES = {"Mage", "Archer", "Berserk", "Healer", "Tank"};

    private final Screen parent;
    private final String featureLabel;
    private final TeslaMapsConfig.ClassFilter filter;
    private boolean wasMouseDown = false;

    private int panelX, panelY, listX, listY, listW;

    public ClassFilterScreen(Screen parent, String featureLabel, TeslaMapsConfig.ClassFilter filter) {
        super(Component.literal("Only as Class"));
        this.parent = parent;
        this.featureLabel = featureLabel;
        this.filter = filter;
    }

    private boolean get(int i) {
        return switch (i) {
            case 0 -> filter.mage;
            case 1 -> filter.archer;
            case 2 -> filter.berserk;
            case 3 -> filter.healer;
            default -> filter.tank;
        };
    }

    private void set(int i, boolean v) {
        switch (i) {
            case 0 -> filter.mage = v;
            case 1 -> filter.archer = v;
            case 2 -> filter.berserk = v;
            case 3 -> filter.healer = v;
            default -> filter.tank = v;
        }
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 56;
        listW = PANEL_W - 32;

        addRenderableWidget(new TeslaButton(panelX + 16, panelY + PANEL_H - 34, PANEL_W - 32, 22, "§fDone", () -> {
            TeslaMapsConfig.save();
            minecraft.gui.setScreen(parent);
        }));
    

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lOnly as Class", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8" + featureLabel, panelX + 16, panelY + 30, 0xFFFFFFFF);

        int rowY = listY;
        for (int i = 0; i < CLASSES.length; i++) {
            boolean value = get(i);
            boolean rowHover = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (clicked && rowHover) { set(i, !value); value = !value; }

            HudStyle.screenButton(ctx, listX, rowY, listW, ROW_H - 2, rowHover, true);
            ctx.text(this.font, "§f" + CLASSES[i], listX + 8, rowY + 7, 0xFFFFFFFF);

            int toggleX = listX + listW - 44, toggleY = rowY + 4;
            ctx.fill(toggleX, toggleY, toggleX + 36, toggleY + 16, value ? GuiStyle.accent() : GuiStyle.toggleOff());
            int knobX = value ? toggleX + 20 : toggleX + 2;
            ctx.fill(knobX, toggleY + 2, knobX + 14, toggleY + 14, GuiStyle.knob());

            rowY += ROW_H;
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        wasMouseDown = isMouseDown;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
