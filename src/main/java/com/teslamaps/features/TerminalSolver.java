package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.dungeon.termsim.TermSimGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.Map;

/**
 * Renders the solution for the floor 7 terminals. Three render modes: Odin
 * (solution only, vanilla items hidden), Normal (solution on top of the vanilla
 * GUI) and Custom GUI (a scaled standalone grid, see
 * {@link com.teslamaps.dungeon.termgui.TermGui}).
 */
public class TerminalSolver {

    // ---- settings ------------------------------------------------------------

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.terminalSolver) return false;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null) return true;
        return switch (term.type) {
            case PANES -> !c.terminalStopPanes;
            case RUBIX -> !c.terminalStopRubix;
            case NUMBERS -> !c.terminalStopNumbers;
            case STARTS_WITH -> !c.terminalStopStartsWith;
            case SELECT -> !c.terminalStopSelect;
            case MELODY -> true;
        };
    }

    public static int slotStyle() { return TeslaMapsConfig.get().terminalSlotStyle; }

    /** Chat, boss bar, labels, carried item and highlights stay hidden while a terminal is open. */
    public static boolean hideClutter() {
        return TeslaMapsConfig.get().terminalHideClutter && TerminalUtils.getCurrentTerm() != null
                && TeslaMapsConfig.get().section("Puzzles & Terminals");
    }

    // ---- keys ----------------------------------------------------------------

    /** Keyboard key or mouse bind (KeyNames encoding) pressed inside a terminal. True = consumed. */
    public static boolean onTerminalKey(int key, Slot focused) {
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null || key == -1) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.section("Puzzles & Terminals")) return false;
        if (c.terminalMelodyKeys && term.type == TerminalTypes.MELODY
                && key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_4) {
            keyClick(term, (key - org.lwjgl.glfw.GLFW.GLFW_KEY_1) * 9 + 16);
            return true;
        }
        if (c.terminalDropKey != -1 && key == c.terminalDropKey) {
            Integer slot = com.teslamaps.dungeon.termgui.TermGui.hoveredActive();
            if (slot == null && focused != null && focused.index < term.type.windowSize) slot = focused.index;
            if (slot != null) keyClick(term, slot);
            return true;
        }
        return false;
    }

    private static void keyClick(TerminalHandler term, int slot) {
        if (enabled()) {
            term.click(slot, 0, hideClicked());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TermSimGUI sim) {
            sim.clickIndex(slot, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_3);
            return;
        }
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_3, net.minecraft.world.inventory.ContainerInput.CLONE, mc.player);
    }
    public static int renderType() { return TeslaMapsConfig.get().terminalRenderType; }
    public static float customTermSize() { return TeslaMapsConfig.get().terminalCustomSize; }
    public static float roundness() { return TeslaMapsConfig.get().terminalRoundness; }
    public static int gap() { return TeslaMapsConfig.get().terminalSlotGap; }
    public static float melodyTermSize() { return TeslaMapsConfig.get().terminalMelodySize; }
    public static boolean showNumbers() { return TeslaMapsConfig.get().terminalShowNumbers; }
    public static int rubixMode() { return TeslaMapsConfig.get().terminalRubixMode; }
    public static boolean hideClicked() { return TeslaMapsConfig.get().terminalHideClicked; }
    public static int terminalReloadThreshold() { return TeslaMapsConfig.get().terminalReloadThreshold; }
    public static int firstClickProt() { return TeslaMapsConfig.get().terminalFirstClickProt; }
    public static boolean shouldFirstClickProtWithTicks() { return TeslaMapsConfig.get().terminalLagProtection; }
    public static int firstClickProtTicks() { return TeslaMapsConfig.get().terminalLagProtectionTicks; }
    private static boolean cancelMelodySolver() { return TeslaMapsConfig.get().terminalCancelMelodySolver; }

    public static int backgroundColor() { return color(TeslaMapsConfig.get().terminalBackgroundColor); }
    public static int panesColor() { return color(TeslaMapsConfig.get().terminalPanesColor); }
    public static int rubixColor1() { return color(TeslaMapsConfig.get().terminalRubixColor1); }
    public static int rubixColor2() { return color(TeslaMapsConfig.get().terminalRubixColor2); }
    public static int oppositeRubixColor1() { return color(TeslaMapsConfig.get().terminalRubixColorNeg1); }
    public static int oppositeRubixColor2() { return color(TeslaMapsConfig.get().terminalRubixColorNeg2); }
    public static int orderColor() { return color(TeslaMapsConfig.get().terminalOrderColor1); }
    public static int orderColor2() { return color(TeslaMapsConfig.get().terminalOrderColor2); }
    public static int orderColor3() { return color(TeslaMapsConfig.get().terminalOrderColor3); }
    public static int orderColor4() { return color(TeslaMapsConfig.get().terminalOrderColor4); }
    public static int startsWithColor() { return color(TeslaMapsConfig.get().terminalStartsWithColor); }
    public static int selectColor() { return color(TeslaMapsConfig.get().terminalSelectColor); }
    public static int melodyColumColor() { return color(TeslaMapsConfig.get().terminalMelodyColumnColor); }
    public static int melodyPointerColor() { return color(TeslaMapsConfig.get().terminalMelodyPointerColor); }
    public static int melodyBackgroundColor() { return color(TeslaMapsConfig.get().terminalMelodyBackgroundColor); }

    private static int color(String hex) {
        return TeslaMapsConfig.parseColor(hex);
    }

    /** True while the terminal window should run at its own GUI scale. */
    public static boolean overridesGuiScale() {
        return enabled() && (renderType() == 0 || renderType() == 1) && TerminalUtils.getCurrentTerm() != null;
    }

    /** GUI scale the terminal window should be forced to while a terminal is open. */
    public static int termSize() {
        if (overridesGuiScale()) {
            int size = TeslaMapsConfig.get().terminalNormalSize;
            return size == 6 ? Options.AUTO_GUI_SCALE : size;
        }
        return 1;
    }

    public static boolean customGuiEnabled() {
        return enabled() && renderType() == 2 && renderMelody();
    }

    private static boolean renderMelody() {
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        return !(cancelMelodySolver() && term != null && term.type == TerminalTypes.MELODY);
    }

    // ---- hooks ---------------------------------------------------------------

    /** Slot clicked in a container screen. Returns true when the click was consumed. */
    public static boolean onSlotClick(int slotIndex, int button) {
        TerminalSounds.onClick(slotIndex, button);
        if (!enabled()) return false;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null) return false;
        term.click(slotIndex, button, hideClicked());
        return true;
    }

    /** Paints over the vanilla container background in Odin render mode. */
    public static void onRender(GuiGraphicsExtractor ctx, int left, int top, int imageWidth, int imageHeight) {
        if (!enabled() || TerminalUtils.getCurrentTerm() == null || !renderMelody() || renderType() != 0) return;
        ctx.fill(left + 7, top + 16, left + imageWidth - 7, top + imageHeight - 96, backgroundColor());
    }

    /** Draws one solution slot. Returns true when the vanilla slot should be skipped. */
    public static boolean onRenderSlot(GuiGraphicsExtractor ctx, Slot slot, AbstractContainerScreen<?> screen) {
        if (!enabled() || !renderMelody()) return false;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null) return false;
        if (slot.index > term.type.windowSize - 1) return false;

        TerminalHandler.SlotRender render = term.getSlotRendering(slot.index);
        if (render != null) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, render.color());
            if (render.text() != null) {
                ctx.centeredText(screen.getFont(), render.text(), slot.x + 8, slot.y + 4, 0xFFFFFFFF);
            }
            return true;
        }
        return renderType() == 0;
    }

    /** Terminals never show item tooltips. */
    public static boolean onDrawTooltip(GuiGraphicsExtractor ctx) {
        return enabled() && TerminalUtils.getCurrentTerm() != null;
    }

    public static void onTerminalOpen() {
        if (renderType() == 0 || renderType() == 1) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(mc::resizeGui);
        }
    }

    /** Always recompute on close: a scale forced by one render mode must not survive a switch. */
    public static void onTerminalClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::resizeGui);
    }

    /** Records the personal best for a solved terminal. */
    public static void onTerminalSolved(TerminalHandler handler) {
        Minecraft mc = Minecraft.getInstance();
        boolean isSim = mc.screen instanceof TermSimGUI;
        Map<String, Float> pbs = isSim
                ? TeslaMapsConfig.get().termSimPbs : TeslaMapsConfig.get().terminalPbs;

        float time = (System.currentTimeMillis() - handler.timeOpened) / 1000f;
        Float oldPB = pbs.get(handler.type.name());
        String suffix;
        if (com.teslamaps.adm.sim.P3Sim.inSim() && !isSim) {
            suffix = oldPB == null ? "" : "§8(PB " + oldPB + ", not saved in ADS)";
        } else if (oldPB == null || oldPB > time) {
            pbs.put(handler.type.name(), time);
            TeslaMapsConfig.save();
            suffix = oldPB == null ? "§7(§d§lNew PB§r§7)" : "§7(§d§lNew PB§r§7) Old PB was §8" + oldPB;
        } else {
            suffix = "§8(§7" + oldPB + "§8)";
        }

        if (TeslaMapsConfig.get().terminalTimes) {
            TerminalUtils.modMessage("§a" + handler.type.termName + (isSim ? " §7(termsim)" : "")
                    + " §7solved in §6" + String.format("%.2f", time) + "s§7! " + suffix);
        }
    }

    public static void resetPbs() {
        TeslaMapsConfig.get().terminalPbs.clear();
        TeslaMapsConfig.save();
        TerminalUtils.modMessage("§6Terminal PBs §fhave been reset.");
    }
}
