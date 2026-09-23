package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pinned value breakdown for one item, opened by a key over a slot. Parts sorted by price with
 * their share of the total, scrollable. After Skyblocker's ValueBreakdownPopup and SkyOcean's
 * ItemValueScreen.
 */
public class ItemValueScreen extends Screen {

    private static final int LINE_H = 11, MARGIN = 10;

    private record Line(String left, String right) {}

    private final Screen parent;
    private final ItemStack stack;
    private final List<Line> lines = new ArrayList<>();
    private final String total;
    private int scroll;
    private int panelX, panelY, panelW, panelH, listY, visible;

    private ItemValueScreen(Screen parent, ItemStack stack, List<EstimatedValue.Part> parts) {
        super(Component.literal("Item Value"));
        this.parent = parent;
        this.stack = stack;
        double sum = 0;
        for (EstimatedValue.Part p : parts) sum += p.value();
        total = EstimatedValue.exact(sum);
        List<EstimatedValue.Part> sorted = new ArrayList<>(parts);
        sorted.sort((a, b) -> Double.compare(b.value(), a.value()));
        for (EstimatedValue.Part p : sorted) {
            if (p.lines().isEmpty()) continue;
            String right = "";
            if (p.value() > 0) {
                double pct = sum > 0 ? p.value() / sum * 100 : 0;
                right = "§6" + EstimatedValue.fmt(p.value()) + " §7(" + (Math.round(pct * 10) / 10.0) + "%)";
            }
            lines.add(new Line(p.lines().get(0), right));
            for (int i = 1; i < p.lines().size(); i++) lines.add(new Line("  " + p.lines().get(i), ""));
        }
    }

    /** Key handler from ConfigHotkey: open over the hovered slot, or close again. */
    public static boolean handleKey(long window, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || window != mc.getWindow().handle()) return false;
        if (mc.screen instanceof ItemValueScreen s) {
            if (action == GLFW.GLFW_PRESS) s.onClose();
            return true;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        Slot slot = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (slot == null || slot.getItem().isEmpty()) return false;
        List<EstimatedValue.Part> parts = EstimatedValue.parts(slot.getItem());
        if (parts.isEmpty()) return false;
        if (action == GLFW.GLFW_PRESS) mc.setScreen(new ItemValueScreen(cs, slot.getItem().copy(), parts));
        return true;
    }

    @Override
    protected void init() {
        int widest = 0;
        for (Line l : lines) widest = Math.max(widest, font.width(l.left()) + font.width(l.right()) + 16);
        panelW = Math.min(width - MARGIN * 2, Math.max(220, widest + 24));
        panelH = Math.min(height - MARGIN * 2, 60 + lines.size() * LINE_H);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listY = panelY + 40;
        visible = Math.max(1, (panelH - 50) / LINE_H);
        scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
        ScreenFit.clamp(this);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = TeslaMapsConfig.get().itemValueScreenKey;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || (key != -1 && event.key() == key)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);

        ctx.item(stack, panelX + 10, panelY + 8);
        ctx.text(font, stack.getHoverName(), panelX + 30, panelY + 8, 0xFFFFFFFF);
        ctx.text(font, "§7Total: §6§l" + total + " coins", panelX + 30, panelY + 20, 0xFFFFFFFF);

        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= lines.size()) break;
            Line l = lines.get(index);
            int y = listY + i * LINE_H;
            ctx.text(font, l.left(), panelX + 12, y, 0xFFFFFFFF);
            if (!l.right().isEmpty()) ctx.text(font, l.right(), panelX + panelW - 12 - font.width(l.right()), y, 0xFFFFFFFF);
        }
        if (lines.size() > visible) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible) + "/" + lines.size();
            ctx.text(font, pos, panelX + panelW - 12 - font.width(pos), panelY + 8, 0xFF808080);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
