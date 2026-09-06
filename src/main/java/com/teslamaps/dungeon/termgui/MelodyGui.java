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
            if (!(row == 0 || (col == 7 && row >= 1 && row <= 4) || (col >= 1 && col <= 5))) return null;

            return new SlotVisual(() -> {
                boolean inSolution = currentSolution().contains(index);
                if (row == 0) {
                    return inSolution ? new TerminalHandler.SlotRender(TerminalSolver.melodyColumColor(), null) : null;
                }
                return new TerminalHandler.SlotRender(
                        inSolution ? TerminalSolver.melodyPointerColor() : TerminalSolver.melodyBackgroundColor(),
                        null);
            });
        });
    }
}
