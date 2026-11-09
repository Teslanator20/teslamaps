package com.teslamaps.adm.practice;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.dungeon.termsim.TermSimGUI;
import com.teslamaps.features.TerminalSolver;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;

// Ported from ADM's TermsimExtras.kt, LeapTermsimProtMixin, NumbersHandlerMixin,
// TerminalNumberMixin and Random Stuff's Hide Numbers' Numbers.
public final class AdmTerms {
    private AdmTerms() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AdmTerms").section(Adm.SECTION)
                .config("ADM Terminals & Practice", b -> {
                    b.label("Terminals");
                    b.info("/termsim inf: a numbers terminal that never ends and has no numbers. Escape to stop; chat gets your pace. No first-click protection there.");
                    b.toggle("Hover Terms", () -> cfg().admHoverTerms, v -> cfg().admHoverTerms = v)
                            .desc("Hovering the slot to click clicks it (numbers: the next number). Termsim only.");
                    b.toggle("Hide Numbers' Numbers", () -> cfg().admHideTermNumbers, v -> cfg().admHideTermNumbers = v)
                            .desc("In the numbers terminal (\"Click in order!\"), hides the numbers 1-10 so only the solver's order colours are left to go on. Applies to /termsim as well.");
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("termsim")
                .then(ClientCommands.literal("inf").executes(c -> {
                    Minecraft.getInstance().schedule(() -> {
                        if (!Adm.on()) { PracticeUtil.msg("§cADM is off (/tmap adg)."); return; }
                        InfNumbersSim.get().open(0L);
                    });
                    return 1;
                }))));

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof TermSimGUI sim)) return;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (Adm.on() && cfg().admHoverTerms) PracticeUtil.safely("hover terms", () -> hover(sim));
            });
        });
    }

    /** Hover Terms: termsim only, never a real terminal (a TermSimGUI never sends clicks). */
    private static void hover(TermSimGUI sim) {
        if (Minecraft.getInstance().gui.screen() != sim) return;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null || term.type == TerminalTypes.MELODY) return;
        Integer slot = TermGui.hoveredActive();
        if (slot == null) {
            Slot s = ((HandledScreenAccessor) sim).getFocusedSlot();
            if (s == null || s.index >= sim.size) return;
            slot = s.index;
        }
        if (term.solution.contains(slot) && term.canClick(slot, 0)) term.click(slot, 0, TerminalSolver.hideClicked());
    }

    /** /termsim inf has no first-click protection. */
    public static boolean noProtection() {
        return InfNumbersSim.active();
    }

    /** Numbers order for /termsim inf, or null for the normal solve. */
    public static java.util.List<Integer> numbersOrder() {
        return InfNumbersSim.active() ? InfNumbersSim.order() : null;
    }

    /** The numbers solver draws no number text. */
    public static boolean hideNumberText() {
        return InfNumbersSim.active() || (Adm.on() && cfg().admHideTermNumbers);
    }

    /** The vanilla stack count of a numbers terminal slot is not drawn. */
    public static boolean hidesTerminalCount(Slot slot) {
        if (!Adm.on() || !cfg().admHideTermNumbers) return false;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        return term != null && term.type == TerminalTypes.NUMBERS && slot.index < term.type.windowSize;
    }
}
