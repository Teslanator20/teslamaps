package com.teslamaps.dungeon.terminals;

import com.teslamaps.dungeon.termgui.MelodyGui;
import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.dungeon.termsim.MelodySim;
import com.teslamaps.dungeon.termsim.NumbersSim;
import com.teslamaps.dungeon.termsim.PanesSim;
import com.teslamaps.dungeon.termsim.RubixSim;
import com.teslamaps.dungeon.termsim.SelectAllSim;
import com.teslamaps.dungeon.termsim.StartsWithSim;
import com.teslamaps.dungeon.termsim.TermSimGUI;
import net.minecraft.world.item.DyeColor;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum TerminalTypes {

    PANES("Correct all the panes!", "^Correct all the panes!$", 45,
            PanesSim::get, () -> TermGui.simple(3, 5, 1, 2)),

    RUBIX("Change all to same color!", "^Change all to same color!$", 45,
            RubixSim::get, () -> TermGui.simple(3, 3, 1, 3)),

    NUMBERS("Click in order!", "^Click in order!$", 36,
            NumbersSim::get, () -> TermGui.simple(2, 7, 1, 1)),

    STARTS_WITH("What starts with: \"*\"?", "^What starts with: '(\\w)'\\?$", 45,
            StartsWithSim::new, () -> TermGui.simple(3, 7, 1, 1)),

    SELECT("Select all the \"*\" items!", "^Select all the ([\\w ]+) items!$", 54,
            SelectAllSim::new, () -> TermGui.simple(4, 7, 1, 1)),

    MELODY("Click the button on time!", "^Click the button on time!$", 54,
            MelodySim::get, MelodyGui::get);

    public final String termName;
    public final Pattern regex;
    public final int windowSize;

    private final Supplier<TermSimGUI> simulatorFactory;
    private final Supplier<TermGui> guiFactory;
    private TermGui gui;

    TerminalTypes(String termName, String regex, int windowSize,
                  Supplier<TermSimGUI> simulatorFactory, Supplier<TermGui> guiFactory) {
        this.termName = termName;
        this.regex = Pattern.compile(regex);
        this.windowSize = windowSize;
        this.simulatorFactory = simulatorFactory;
        this.guiFactory = guiFactory;
    }

    public TermSimGUI getSimulator() {
        return simulatorFactory.get();
    }

    public TermGui getGUI() {
        if (gui == null) gui = guiFactory.get();
        return gui;
    }

    public static TerminalTypes match(String guiName) {
        for (TerminalTypes type : values()) {
            if (type.regex.matcher(guiName).matches()) return type;
        }
        return null;
    }

    public static TerminalHandler openHandler(String guiName) {
        TerminalTypes type = match(guiName);
        if (type == null) return null;
        return switch (type) {
            case PANES -> new PanesHandler();
            case RUBIX -> new RubixHandler();
            case NUMBERS -> new NumbersHandler();
            case STARTS_WITH -> {
                Matcher m = STARTS_WITH.regex.matcher(guiName);
                if (!m.find()) {
                    TerminalUtils.modMessage("Failed to find letter, please report this!");
                    yield null;
                }
                yield new StartsWithHandler(m.group(1));
            }
            case SELECT -> {
                Matcher m = SELECT.regex.matcher(guiName);
                String name = m.find() ? m.group(1).replace("SILVER", "LIGHT GRAY") : null;
                DyeColor color = null;
                if (name != null) {
                    for (DyeColor dye : DyeColor.values()) {
                        if (dye.name().replace("_", " ").equalsIgnoreCase(name)) { color = dye; break; }
                    }
                }
                if (color == null) {
                    TerminalUtils.modMessage("Failed to find letter, please report this!");
                    yield null;
                }
                yield new SelectAllHandler(color);
            }
            case MELODY -> new MelodyHandler();
        };
    }
}
