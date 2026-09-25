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
    private HudEditElement selected = null;
    private boolean wasMiddleDown = false;
    private long resetArmed = 0;
    private String flash = null;
    private long flashAt = 0;
    private TeslaButton resetAllBtn;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new TeslaButton(this.width - 110, 6, 100, 22, "§fDone", this::onClose));
        resetAllBtn = null;
        if (extras() && TeslaMapsConfig.get().hudEditReset) {
            resetAllBtn = addRenderableWidget(new TeslaButton(this.width - 110, 32, 100, 22, "Reset All", this::resetAll));
        }
        ScreenFit.clamp(this); // keep every widget reachable at any gui scale
    }

    private static boolean extras() {
        return TeslaMapsConfig.get().hudEditExtras;
    }

    private void flash(String msg) {
        flash = msg;
        flashAt = System.currentTimeMillis();
    }

    private void resetAll() {
        long now = System.currentTimeMillis();
        if (now - resetArmed > 3000) {
            resetArmed = now;
            flash("Click Reset All again to put every element back to its default");
            return;
        }
        resetArmed = 0;
        TeslaMapsConfig defaults = new TeslaMapsConfig();
        for (HudEditElement e : HudEditElement.all()) e.resetToDefault(defaults);
        TeslaMapsConfig.save();
        flash("Every element reset");
    }

    /** Selected element, else the one under the cursor. */
    private HudEditElement target() {
        if (selected != null && visible.contains(selected)) return selected;
        var w = minecraft.getWindow();
        double mx = minecraft.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();
        return at((int) mx, (int) my);
    }

    private void move(HudEditElement e, int dx, int dy) {
        e.bakeShift();
        e.setX(Math.max(0, Math.min(this.width - e.width(), e.x() + dx)));
        e.setY(Math.max(0, Math.min(this.height - e.height(), e.y() + dy)));
        e.recordAnchor();
        TeslaMapsConfig.save();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == GLFW.GLFW_KEY_TAB) {
            showAll = !showAll;
            return true;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (extras() && dragging == null) {
            HudEditElement t = target();
            if (t != null && c.hudEditReset && k == GLFW.GLFW_KEY_R) {
                t.resetToDefault(new TeslaMapsConfig());
                TeslaMapsConfig.save();
                flash(t.label + " reset");
                return true;
            }
            if (t != null && c.hudEditNudge) {
                int step = minecraft.hasShiftDown() ? 10 : 1;
                switch (k) {
                    case GLFW.GLFW_KEY_LEFT -> { move(t, -step, 0); return true; }
                    case GLFW.GLFW_KEY_RIGHT -> { move(t, step, 0); return true; }
                    case GLFW.GLFW_KEY_UP -> { move(t, 0, -step); return true; }
                    case GLFW.GLFW_KEY_DOWN -> { move(t, 0, step); return true; }
                    case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { t.nudgeScale(0.1f); TeslaMapsConfig.save(); return true; }
                    case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { t.nudgeScale(-0.1f); TeslaMapsConfig.save(); return true; }
                    default -> {}
                }
            }
        }
        return super.keyPressed(event);
    }

    private void rightClick(HudEditElement e) {
        Boolean v = e.toggle();
        if (v == null) { flash(e.label + " has no single on/off switch - use the settings"); return; }
        TeslaMapsConfig.save();
        String sec = e.section();
        boolean secOff = sec != null && !TeslaMapsConfig.get().section(sec);
        flash(e.label + (v ? " §aenabled" : " §cdisabled") + (v && secOff ? " §7(\"" + sec + "\" is switched off)" : ""));
    }

    private void jump(HudEditElement e) {
        TeslaMapsConfig.save();
        MapConfigScreen.jumpTo(e.section(), e.label);
        minecraft.setScreen(MapConfigScreen.open());
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
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean extras = extras();
        boolean showOff = extras && cfg.hudEditShowDisabled;

        // TAB drops the situation check only, a switched off element stays hidden either way
        visible.clear();
        for (HudEditElement e : HudEditElement.all()) {
            if (e.shown(showAll) || (showOff && e.shownDisabled(showAll))) visible.add(e);
        }
        if (dragging != null && !visible.contains(dragging)) dragging = null;

        if (isMouseDown && !wasMouseDown) {
            HudEditElement hit = at(mouseX, mouseY);
            if (hit != null) {
                selected = hit;
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
        if (extras && cfg.hudEditJump) {
            boolean mid = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            boolean midClicked = mid && !wasMiddleDown;
            wasMiddleDown = mid;
            HudEditElement hit = midClicked && dragging == null ? at(mouseX, mouseY) : null;
            if (hit != null) {
                jump(hit);
                return;
            }
        }

        if (dragging != null) {
            int w = dragging.width(), h = dragging.height();
            dragging.setX(Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX)));
            dragging.setY(Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY)));
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        HudEditElement hovered = null;
        for (HudEditElement e : visible) {
            int bx = e.boxX(), by = e.boxY(), w = e.width(), h = e.height();
            if (e.hit(mouseX, mouseY)) hovered = e;
            if (showOff && !e.enabledNow()) {
                context.fill(bx, by, bx + w, by + h, 0x40FFFFFF);
                context.text(this.font, "§7" + e.label + " (off)", bx, by - 12, 0xFFFFFFFF);
                continue;
            }
            e.drawPreview(context, minecraft);
            if (e == dragging || e.hit(mouseX, mouseY)) {
                int borderColor = e == dragging ? e.dragColor() : 0xFF888888;
                context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
                context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
                context.fill(bx - 2, by, bx, by + h, borderColor);
                context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
            }
            context.text(this.font, e.title(), bx, by - 12, e == selected && extras ? 0xFFFFFF55 : 0xFFFFFFFF);
        }

        String instructions = dragging != null
                ? "Release to place"
                : (showAll
                        ? "Showing every element | TAB for current situation only"
                        : "Drag to move | Scroll to resize | TAB shows all elements");
        if (extras && dragging == null) {
            if (flash != null && System.currentTimeMillis() - flashAt < 2500) instructions = flash;
            else if (hovered != null) instructions = hovered.label + " | x " + hovered.x() + " y " + hovered.y()
                    + " | " + String.format("%.2fx", hovered.scale()) + keyHints();
        }
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.HudStyle.screenPanel(context,
                this.width / 2f - textWidth / 2f - 8, 8, textWidth + 16, 18, 9);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);

        if (resetAllBtn != null) resetAllBtn.setMessage(Component.literal(
                System.currentTimeMillis() - resetArmed < 3000 ? "§cConfirm Reset" : "Reset All"));

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private static String keyHints() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        StringBuilder sb = new StringBuilder();
        if (c.hudEditNudge) sb.append(" | Arrows move, +/- scale");
        if (c.hudEditReset) sb.append(" | R reset");
        if (c.hudEditShowDisabled && c.hudEditRightClickToggle) sb.append(" | Right-click on/off");
        if (c.hudEditJump) sb.append(" | Middle-click settings");
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && extras() && c.hudEditShowDisabled && c.hudEditRightClickToggle && dragging == null) {
            HudEditElement hit = at((int) event.x(), (int) event.y());
            if (hit != null) {
                rightClick(hit);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
