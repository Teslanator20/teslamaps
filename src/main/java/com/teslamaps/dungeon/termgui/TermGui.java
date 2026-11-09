package com.teslamaps.dungeon.termgui;

import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.features.TerminalSolver;
import com.teslamaps.render.gui.TeslaGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Scaled stand-in for the vanilla terminal window: draws the solution grid in
 * the middle of the screen and routes clicks back to the live terminal handler.
 * Not pixel perfect against vanilla, but close enough.
 */
public abstract class TermGui {

    private static final int SLOT_SIZE = 24;

    public interface ContentRenderer {
        void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height);
    }

    protected record SlotVisual(Supplier<TerminalHandler.SlotRender> resolve, ContentRenderer onRenderContent) {
        SlotVisual(Supplier<TerminalHandler.SlotRender> resolve) { this(resolve, null); }
    }

    private record SlotBox(int slotIndex, int bx, int by, int size, SlotVisual visual) {
        boolean containsBase(float x, float y) {
            return x >= bx && y >= by && x < bx + size && y < by + size;
        }
    }

    private record Grid(float originX, float originY, int w, int h, List<SlotBox> slots) {}

    private Grid grid;
    private Integer hoveredSlotIndex;

    protected float guiScale() {
        return TerminalSolver.customTermSize();
    }

    public abstract void buildTerminal(AbstractContainerScreen<?> screen);

    protected static List<Integer> currentSolution() {
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        return term == null ? List.of() : term.solution;
    }

    // ---- active-gui dispatch -------------------------------------------------

    /** The custom GUI that should be drawn right now, or null. */
    public static TermGui active() {
        if (!TerminalSolver.customGuiEnabled()) return null;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null) return null;
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) return null;
        return term.type.getGUI();
    }

    public static boolean renderActive(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        TermGui gui = active();
        if (gui == null) return false;
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return false;
        gui.buildTerminal(screen);
        gui.render(ctx, mouseX, mouseY);
        return true;
    }

    public static boolean clickActive(double mouseX, double mouseY, int button) {
        TermGui gui = active();
        if (gui == null) return false;
        Grid g = gui.grid;
        if (g != null) {
            float bx = ((float) mouseX - g.originX) / gui.guiScale();
            float by = ((float) mouseY - g.originY) / gui.guiScale();
            gui.hoveredSlotIndex = null;
            for (SlotBox slot : g.slots) {
                if (slot.containsBase(bx, by)) { gui.hoveredSlotIndex = slot.slotIndex; break; }
            }
            TerminalHandler term = TerminalUtils.getCurrentTerm();
            if (gui.hoveredSlotIndex != null && term != null) {
                term.click(gui.hoveredSlotIndex, button, TerminalSolver.hideClicked());
            }
        }
        return true;
    }

    /** Slot under the mouse in the active custom GUI, or null. */
    public static Integer hoveredActive() {
        TermGui gui = active();
        return gui == null ? null : gui.hoveredSlotIndex;
    }

    public static boolean keyActive(KeyEvent input) {
        TermGui gui = active();
        if (gui == null || !isTerminalOverrideKey(input)) return false;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (gui.hoveredSlotIndex != null && term != null) {
            term.click(gui.hoveredSlotIndex,
                    input.hasControlDown() ? GLFW.GLFW_MOUSE_BUTTON_2 : GLFW.GLFW_MOUSE_BUTTON_1,
                    TerminalSolver.hideClicked());
        }
        return true;
    }

    private static boolean isTerminalOverrideKey(KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyDrop.matches(event)) return true;
        for (var key : mc.options.keyHotbarSlots) if (key.matches(event)) return true;
        return false;
    }

    // ---- layout + drawing ----------------------------------------------------

    protected void buildTerminalGrid(AbstractContainerScreen<?> screen, int rows, int cols,
                                     int startRow, int startCol,
                                     java.util.function.IntFunction<SlotVisual> slotFactory) {
        int gap = TerminalSolver.gap();
        float scale = guiScale();
        int w = cols * SLOT_SIZE + (cols - 1) * gap;
        int h = rows * SLOT_SIZE + (rows - 1) * gap;

        List<SlotBox> slots = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = (startRow + row) * 9 + (startCol + col);
                SlotVisual visual = slotFactory.apply(index);
                if (visual == null) continue;
                slots.add(new SlotBox(index, col * (SLOT_SIZE + gap), row * (SLOT_SIZE + gap), SLOT_SIZE, visual));
            }
        }
        grid = new Grid((screen.width - w * scale) / 2f, (screen.height - h * scale) / 2f, w, h, slots);
    }

    private void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Grid g = grid;
        if (g == null) return;
        float scale = guiScale();
        int padding = 2;
        float radius = TerminalSolver.roundness();
        float baseMX = (mouseX - g.originX) / scale;
        float baseMY = (mouseY - g.originY) / scale;
        hoveredSlotIndex = null;

        ctx.pose().pushMatrix();
        ctx.pose().translate(g.originX, g.originY);
        ctx.pose().scale(scale, scale);

        TeslaGui.roundedRect(ctx, -padding, -padding, g.w + padding * 2f, g.h + padding * 2f,
                radius, TerminalSolver.backgroundColor());

        for (SlotBox slot : g.slots) {
            TerminalHandler.SlotRender render = slot.visual.resolve().get();
            if (render == null) continue;
            if (slot.containsBase(baseMX, baseMY)) hoveredSlotIndex = slot.slotIndex;
            drawSlot(ctx, slot.bx, slot.by, slot.size, radius, render.color());
            if (slot.visual.onRenderContent() != null) {
                slot.visual.onRenderContent().render(ctx, slot.bx, slot.by, slot.size, slot.size);
            }
        }

        ctx.pose().popMatrix();
    }

    private static void drawSlot(GuiGraphicsExtractor ctx, int x, int y, int size, float radius, int color) {
        switch (TerminalSolver.slotStyle()) {
            case 1 -> ctx.fill(x, y, x + size, y + size, color);
            case 2 -> {
                ctx.fill(x, y, x + size, y + size, TeslaGui.withAlpha(color, 40));
                ctx.fill(x, y, x + size, y + 1, color);
                ctx.fill(x, y + size - 1, x + size, y + size, color);
                ctx.fill(x, y + 1, x + 1, y + size - 1, color);
                ctx.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
            }
            case 3 -> {
                int a = color & 0xFF000000;
                ctx.fill(x, y, x + size, y + size, a | shade(color, 0.55f));
                ctx.fill(x, y, x + size - 1, y + size - 1, a | shade(color, 1.35f));
                ctx.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
            }
            default -> TeslaGui.roundedRect(ctx, x, y, size, size, radius, color);
        }
    }

    private static int shade(int color, float f) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((color & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    protected static void renderSlotText(GuiGraphicsExtractor ctx, String text,
                                         int x, int y, int width, int height, int color) {
        Minecraft mc = Minecraft.getInstance();
        ctx.text(mc.font, text,
                x + (width - mc.font.width(text)) / 2,
                y + (height - mc.font.lineHeight) / 2 + 1,
                color, true);
    }

    /** A plain rows x cols grid driven entirely by the handler's slot rendering. */
    public static TermGui simple(int rows, int cols, int startRow, int startCol) {
        return new TermGui() {
            @Override
            public void buildTerminal(AbstractContainerScreen<?> screen) {
                buildTerminalGrid(screen, rows, cols, startRow, startCol, slotIndex -> new SlotVisual(
                        () -> resolve(slotIndex),
                        (ctx, x, y, w, h) -> {
                            TerminalHandler.SlotRender render = resolve(slotIndex);
                            if (render != null && render.text() != null) {
                                renderSlotText(ctx, render.text(), x, y, w, h, 0xFFFFFFFF);
                            }
                        }));
            }

            private TerminalHandler.SlotRender resolve(int slotIndex) {
                TerminalHandler term = TerminalUtils.getCurrentTerm();
                return term == null ? null : term.getSlotRendering(slotIndex);
            }
        };
    }
}
