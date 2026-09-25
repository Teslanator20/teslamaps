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

    // GUI-043, after FrankyAddons' ModMenuScreen fling: last 8 drag samples, bounce off the edges
    private final java.util.ArrayDeque<double[]> samples = new java.util.ArrayDeque<>();
    private final java.util.Map<HudEditElement, double[]> flying = new java.util.HashMap<>();
    private long lastFrame = 0;
    private int lastDragX, lastDragY;

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
                flying.remove(hit);
                samples.clear();
                lastDragX = hit.x();
                lastDragY = hit.y();
                dragOffsetX = mouseX - hit.x();
                dragOffsetY = mouseY - hit.y();
            }
        }
        long now = net.minecraft.util.Util.getMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame) / 1000.0;
        lastFrame = now;
        if (!isMouseDown && wasMouseDown && dragging != null) {
            if (TeslaMapsConfig.get().hudEditFling) fling(dragging);
            dragging.recordAnchor();
            dragging = null;
            TeslaMapsConfig.save();
        }
        wasMouseDown = isMouseDown;

        if (dragging != null) {
            int w = dragging.width(), h = dragging.height();
            dragging.setX(Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX)));
            dragging.setY(Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY)));
            if (dt > 0) {
                samples.addLast(new double[]{dragging.x() - lastDragX, dragging.y() - lastDragY, dt});
                while (samples.size() > 8) samples.removeFirst();
            }
            lastDragX = dragging.x();
            lastDragY = dragging.y();
        }
        if (!flying.isEmpty()) stepFling(dt);

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

    private void fling(HudEditElement e) {
        double dx = 0, dy = 0, t = 0;
        for (double[] s : samples) { dx += s[0]; dy += s[1]; t += s[2]; }
        samples.clear();
        if (t <= 0) return;
        double vx = Math.max(-2500, Math.min(2500, dx / t * 0.8));
        double vy = Math.max(-2500, Math.min(2500, dy / t * 0.8));
        if (Math.abs(vx) + Math.abs(vy) < 60) return;
        flying.put(e, new double[]{e.x(), e.y(), vx, vy});
    }

    private void stepFling(double dt) {
        if (dt <= 0) return;
        double damp = Math.pow(0.35, dt);
        var it = flying.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            HudEditElement e = en.getKey();
            double[] f = en.getValue();
            double nx = f[0] + f[2] * dt, ny = f[1] + f[3] * dt;
            int maxX = Math.max(0, this.width - e.width()), maxY = Math.max(0, this.height - e.height());
            if (nx <= 0) { nx = 0; f[2] = -f[2]; } else if (nx >= maxX) { nx = maxX; f[2] = -f[2]; }
            if (ny <= 0) { ny = 0; f[3] = -f[3]; } else if (ny >= maxY) { ny = maxY; f[3] = -f[3]; }
            f[0] = nx; f[1] = ny;
            f[2] *= damp; f[3] *= damp;
            e.setX((int) Math.round(nx));
            e.setY((int) Math.round(ny));
            if (Math.abs(f[2]) + Math.abs(f[3]) < 8) {
                e.recordAnchor();
                it.remove();
                TeslaMapsConfig.save();
            }
        }
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
