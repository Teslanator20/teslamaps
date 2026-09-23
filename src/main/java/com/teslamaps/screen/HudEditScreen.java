package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** /tmap gui. Every element comes from {@link HudEditElement#all()}. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean wasMouseDown = false;
    private boolean showAll = false;

    /** Elements drawn this frame, in draw order. Anything not in here cannot be hovered or dragged either. */
    private final List<HudEditElement> visible = new ArrayList<>();
    private HudEditElement dragging = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new TeslaButton(this.width - 110, 6, 100, 22, "§fDone", this::onClose));
        ScreenFit.clamp(this); // keep every widget reachable at any gui scale
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            showAll = !showAll;
            return true;
        }
        return super.keyPressed(event);
    }

    /** Topmost (last drawn) element under the cursor. */
    private HudEditElement at(int mouseX, int mouseY) {
        for (int i = visible.size() - 1; i >= 0; i--) {
            if (visible.get(i).hit(mouseX, mouseY)) return visible.get(i);
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // TAB drops the situation check only, a switched off element stays hidden either way
        visible.clear();
        for (HudEditElement e : HudEditElement.all()) {
            if (e.shown(showAll)) visible.add(e);
        }
        if (dragging != null && !visible.contains(dragging)) dragging = null;

        if (isMouseDown && !wasMouseDown) {
            HudEditElement hit = at(mouseX, mouseY);
            if (hit != null) {
                hit.bakeShift();
                dragging = hit;
                dragOffsetX = mouseX - hit.x();
                dragOffsetY = mouseY - hit.y();
            }
        }
        if (!isMouseDown && wasMouseDown && dragging != null) {
            dragging.recordAnchor();
            dragging = null;
            TeslaMapsConfig.save();
        }
        wasMouseDown = isMouseDown;

        if (dragging != null) {
            int w = dragging.width(), h = dragging.height();
            dragging.setX(Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX)));
            dragging.setY(Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY)));
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        for (HudEditElement e : visible) {
            e.drawPreview(context, minecraft);
            int bx = e.boxX(), by = e.boxY(), w = e.width(), h = e.height();
            if (e == dragging || e.hit(mouseX, mouseY)) {
                int borderColor = e == dragging ? e.dragColor() : 0xFF888888;
                context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
                context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
                context.fill(bx - 2, by, bx, by + h, borderColor);
                context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
            }
            context.text(this.font, e.title(), bx, by - 12, 0xFFFFFFFF);
        }

        String instructions = dragging != null
                ? "Release to place"
                : (showAll
                        ? "Showing every element | TAB for current situation only"
                        : "Drag to move | Scroll to resize | TAB shows all elements");
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.HudStyle.screenPanel(context,
                this.width / 2f - textWidth / 2f - 8, 8, textWidth + 16, 18, 9);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudEditElement hit = at((int) mouseX, (int) mouseY);
        if (hit != null) {
            hit.scroll(verticalAmount);
            TeslaMapsConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
