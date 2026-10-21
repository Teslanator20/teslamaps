package com.teslamaps.dungeon.termgui;

import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class MelodyGui extends TermGui {

    private static MelodyGui instance;

    public static MelodyGui get() {
        if (instance == null) instance = new MelodyGui();
        return instance;
    }

    @Override
    protected float guiScale() {
        return TerminalSolver.melodyTermSize();
    }

    @Override
    public void buildTerminal(AbstractContainerScreen<?> screen) {
        buildTerminalGrid(screen, 5, 7, 0, 1, index -> {
            int row = index / 9;
            int col = index % 9;
            boolean border = row == 0 || row == 4 || col == 6;

            return new SlotVisual(() -> {
                if (currentSolution().contains(index)) {
                    int color = row == 0 || row == 4 ? TerminalSolver.melodyColumColor() : TerminalSolver.melodyPointerColor();
                    return new TerminalHandler.SlotRender(color, null);
                }
                return border ? null : new TerminalHandler.SlotRender(TerminalSolver.melodyBackgroundColor(), null);
            });
        });
    }
}
