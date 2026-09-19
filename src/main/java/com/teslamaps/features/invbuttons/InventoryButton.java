package com.teslamaps.features.invbuttons;

/**
 * One button placed around a container screen.
 * Same fields as NotEnoughUpdates' Inventory Buttons, so its shared codes and presets load as-is.
 * Ported from GanKura.
 */
public class InventoryButton {

    /** Offset from the screen's top left, or from the anchored corner. */
    public int x;
    public int y;

    /** Only draw on the player inventory. */
    public boolean playerInvOnly;

    /** Count from the right/bottom edge instead, so screens of different width keep the look. */
    public boolean anchorRight;
    public boolean anchorBottom;

    /** Frame style, 0-4. */
    public int backgroundIndex;

    public String command = "";
    public String icon = "";

    public InventoryButton() {}

    public InventoryButton(int x, int y, String icon, boolean playerInvOnly,
                           boolean anchorRight, boolean anchorBottom, int backgroundIndex, String command) {
        this.x = x;
        this.y = y;
        this.icon = icon;
        this.playerInvOnly = playerInvOnly;
        this.anchorRight = anchorRight;
        this.anchorBottom = anchorBottom;
        this.backgroundIndex = backgroundIndex;
        this.command = command;
    }

    /** A button without a command is just a free slot - it only shows in the editor. */
    public boolean isActive() {
        return command != null && !command.trim().isEmpty();
    }

    public String commandOrEmpty() {
        return command == null ? "" : command;
    }

    public String iconOrEmpty() {
        return icon == null ? "" : icon;
    }

    public InventoryButton copy() {
        return new InventoryButton(x, y, iconOrEmpty(), playerInvOnly,
                anchorRight, anchorBottom, backgroundIndex, commandOrEmpty());
    }
}
