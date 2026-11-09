package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Plot teleport grid drawn beside the player inventory in the Garden. Layout
 * constants (panel size, slot pitch, button row) are Skyblocker's; the panel
 * itself is drawn with our own shapes instead of their texture.
 */
public class GardenPlotsOverlay {

    private static final int PANEL_WIDTH = 104;
    private static final int PANEL_HEIGHT = 132;
    private static final int SPACING = 4;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 7;
    private static final int GRID_Y = 17;
    private static final int TITLE_BAR_HEIGHT = 15;
    private static final int BUTTON_SIZE = 20;

    private static int panelX;
    private static int panelY;
    private static int hoveredSlot = -1;
    private static int hoveredButton = -1;
    private static int editingSlot = -1;
    private static boolean titleBarHovered;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static boolean dragging;

    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return TeslaMapsConfig.get().gardenPlotsOverlay
                && mc.gui.screen() instanceof InventoryScreen
                && SkyblockUtils.isInGarden();
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int inventoryRight = accessor.getX() + accessor.getImageWidth();
        int inventoryTop = accessor.getY();
        if (!dragging) {
            panelX = inventoryRight + SPACING + config.gardenPlotsX;
            panelY = inventoryTop + config.gardenPlotsY;
        }

        drawPanel(ctx, panelX, panelY);

        ctx.text(mc.font, editingSlot < 0
                        ? Component.literal("Plots")
                        : Component.literal("Custom Icon"),
                panelX + 8, panelY + 6, 0xFFE6EBF2, false);

        if (!GardenPlots.hasAny()) {
            ctx.text(mc.font, Component.literal("§7Open the Desk and"),
                    panelX + 8, panelY + GRID_Y + 4, 0xFFFFFFFF, false);
            ctx.text(mc.font, Component.literal("§7click Configure Plots"),
                    panelX + 8, panelY + GRID_Y + 15, 0xFFFFFFFF, false);
            ctx.text(mc.font, Component.literal("§7once to load names"),
                    panelX + 8, panelY + GRID_Y + 26, 0xFFFFFFFF, false);
            hoveredSlot = -1;
            drawButtons(ctx, mouseX, mouseY);
            titleBarHovered = mouseX > panelX + 4 && mouseY > panelY + 4
                    && mouseX < panelX + PANEL_WIDTH - 4 && mouseY < panelY + TITLE_BAR_HEIGHT;
            drawTooltip(ctx, mouseX, mouseY, new boolean[GardenPlots.PLOT_COUNT]);
            return;
        }

        boolean[] infested = editingSlot < 0 ? GardenPlots.infestedSlots() : new boolean[GardenPlots.PLOT_COUNT];
        hoveredSlot = -1;
        // Skyblocker flashes the pest border on a 512 ms phase of wall time.
        boolean flash = (System.currentTimeMillis() & 512) != 0;

        int count = editingSlot < 0 ? GardenPlots.PLOT_COUNT : GardenPlots.CUSTOM_ICONS.length;
        for (int i = 0; i < count; i++) {
            int slotX = panelX + GRID_X + (i % 5) * SLOT_SIZE;
            int slotY = panelY + GRID_Y + (i / 5) * SLOT_SIZE;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

            ItemStack stack = editingSlot < 0
                    ? GardenPlots.icon(i)
                    : new ItemStack(GardenPlots.CUSTOM_ICONS[i]);

            TeslaGui.roundedRect(ctx, slotX, slotY, SLOT_SIZE - 1, SLOT_SIZE - 1, 3,
                    TeslaGui.withAlpha(0xFFFFFF, hovered ? 46 : 20));

            if (!stack.isEmpty()) {
                if (hovered) {
                    // Same 1.125 pop Skyblocker uses on the hovered icon.
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(slotX, slotY);
                    ctx.pose().scale(1.125f, 1.125f);
                    ctx.item(stack, 0, 0);
                    ctx.pose().popMatrix();
                } else {
                    ctx.item(stack, slotX + 1, slotY + 1);
                }
            }

            String pests = editingSlot < 0 ? GardenPlots.pestCountText(i) : null;
            if (pests != null) {
                ctx.text(mc.font, pests, slotX + 17 - mc.font.width(pests), slotY + 9, 0xFFFFAA00, true);
            }

            if (infested[Math.min(i, infested.length - 1)] && editingSlot < 0 && flash) {
                TeslaGui.roundedOutline(ctx, slotX, slotY, SLOT_SIZE - 1, SLOT_SIZE - 1, 3, 1.0f, 0xFFFF5555);
            }

            if (hovered) hoveredSlot = i;
        }

        drawButtons(ctx, mouseX, mouseY);

        titleBarHovered = mouseX > panelX + 4 && mouseY > panelY + 4
                && mouseX < panelX + PANEL_WIDTH - 4 && mouseY < panelY + TITLE_BAR_HEIGHT;

        drawTooltip(ctx, mouseX, mouseY, infested);
    }

    private static void drawPanel(GuiGraphicsExtractor ctx, int x, int y) {
        TeslaGui.shadow(ctx, x, y + 2, PANEL_WIDTH, PANEL_HEIGHT, 6, 8, TeslaGui.withAlpha(0x000000, 90));
        TeslaGui.roundedRect(ctx, x, y, PANEL_WIDTH, PANEL_HEIGHT, 6, TeslaGui.withAlpha(0x141A26, 235));
        TeslaGui.roundedRectGradient(ctx, x + 1, y + 1, PANEL_WIDTH - 2, 24, 5,
                TeslaGui.withAlpha(0xFFFFFF, 18), TeslaGui.withAlpha(0xFFFFFF, 0));
        TeslaGui.roundedOutline(ctx, x + 0.5f, y + 0.5f, PANEL_WIDTH - 1, PANEL_HEIGHT - 1, 6, 1.0f,
                TeslaGui.withAlpha(0xFFFFFF, 46));
    }

    private static int buttonX(int index) {
        return switch (index) {
            case 0 -> panelX + GRID_X;
            case 1 -> panelX + PANEL_WIDTH - 7 - BUTTON_SIZE * 2 - 2;
            default -> panelX + PANEL_WIDTH - 7 - BUTTON_SIZE;
        };
    }

    private static void drawButtons(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (editingSlot >= 0) {
            hoveredButton = -1;
            return;
        }

        ItemStack[] icons = {
                new ItemStack(Items.BOOK),
                new ItemStack(Items.ENDER_EYE),
                new ItemStack(Items.BED.red())
        };

        int buttonY = panelY + PANEL_HEIGHT - 24;
        hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = buttonX(i);
            boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE
                    && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
            if (hovered) hoveredButton = i;

            TeslaGui.roundedRect(ctx, x, buttonY, BUTTON_SIZE, BUTTON_SIZE, 3,
                    TeslaGui.withAlpha(0xFFFFFF, hovered ? 46 : 20));
            ctx.item(icons[i], x + 2, buttonY + 2);
        }
    }

    private static void drawTooltip(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean[] infested) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();

        if (hoveredButton >= 0) {
            lines.add(Component.literal(switch (hoveredButton) {
                case 0 -> "Open Desk";
                case 1 -> "Warp to Garden";
                default -> "Set Spawn";
            }));
        } else if (hoveredSlot >= 0 && editingSlot >= 0) {
            lines.add(new ItemStack(GardenPlots.CUSTOM_ICONS[hoveredSlot]).getHoverName());
        } else if (hoveredSlot >= 0) {
            ItemStack stack = GardenPlots.icon(hoveredSlot);
            if (stack.isEmpty()) return;
            lines.add(hoveredSlot == GardenPlots.BARN_SLOT
                    ? stack.getHoverName()
                    : Component.literal("Plot ").append(stack.getHoverName()));
            if (infested[hoveredSlot]) lines.add(Component.literal("§c§lGROSS PESTS!"));
            String pests = GardenPlots.pestCountText(hoveredSlot);
            if (pests != null) lines.add(Component.literal("§6" + pests + (pests.equals("1") ? " pest" : " pests")));
            lines.add(Component.empty());
            lines.add(Component.literal("§e§lClick to teleport"));
            lines.add(Component.literal("§7Right click to change icon"));
        }

        if (!lines.isEmpty()) ctx.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
    }

    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (!shouldRender()) return false;

        if (editingSlot >= 0) {
            if (hoveredSlot >= 0) {
                // Index 0 is the "none" barrier, which clears back to the server icon.
                GardenPlots.setCustomIcon(editingSlot, hoveredSlot == 0 ? -1 : hoveredSlot);
                editingSlot = -1;
                return true;
            }
            if (isInsidePanel(mouseX, mouseY)) {
                editingSlot = -1;
                return true;
            }
            return false;
        }

        if (hoveredButton >= 0) {
            GardenPlots.command(switch (hoveredButton) {
                case 0 -> "desk";
                case 1 -> "warp garden";
                default -> "setspawn";
            });
            return true;
        }

        if (titleBarHovered) {
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && Minecraft.getInstance().hasShiftDown()) {
                TeslaMapsConfig.get().gardenPlotsX = 0;
                TeslaMapsConfig.get().gardenPlotsY = 0;
                TeslaMapsConfig.save();
            } else {
                dragging = true;
                dragOffsetX = (int) mouseX - panelX;
                dragOffsetY = (int) mouseY - panelY;
            }
            return true;
        }

        if (hoveredSlot < 0) return false;

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            if (hoveredSlot != GardenPlots.BARN_SLOT) editingSlot = hoveredSlot;
            return true;
        }

        GardenPlots.teleport(hoveredSlot);
        if (TeslaMapsConfig.get().gardenPlotsCloseOnClick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) mc.gui.screen().onClose();
        }
        return true;
    }

    public static boolean handleDrag(double mouseX, double mouseY) {
        if (!dragging || !shouldRender()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;

        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int inventoryRight = accessor.getX() + accessor.getImageWidth();
        panelX = Math.max((int) mouseX - dragOffsetX, inventoryRight + SPACING);
        panelY = (int) mouseY - dragOffsetY;
        return true;
    }

    public static boolean handleRelease() {
        if (!dragging) return false;
        dragging = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
            TeslaMapsConfig.get().gardenPlotsX = panelX - accessor.getX() - accessor.getImageWidth() - SPACING;
            TeslaMapsConfig.get().gardenPlotsY = panelY - accessor.getY();
            TeslaMapsConfig.save();
        }
        return true;
    }

    private static boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT;
    }

    public static void onScreenClosed() {
        editingSlot = -1;
        dragging = false;
    }
}
