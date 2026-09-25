package com.teslamaps.features.invbuttons;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Buttons around a container screen that run a command on click.
 * Ported from GanKura's InventoryButtonHandler (itself NotEnoughUpdates' Inventory Buttons).
 * Placement is edited in {@link com.teslamaps.screen.InventoryButtonEditorScreen}.
 */
public final class InventoryButtons {

    private static final int OPAQUE = 0xFFFFFFFF;

    // a button sitting over a puzzle grid is in the way
    private static final Set<String> DUNGEON_MENUS = Set.of(
            "Spirit Leap",
            "Revive A Teammate",
            "Click in order!",
            "Click the button on time!",
            "Correct all the panes!",
            "Change all to same color!");

    private static final List<String> DUNGEON_MENU_PREFIXES = List.of(
            "What starts with",
            "Select all the");

    private static final Component CONFIRM_TEXT =
            Component.literal("Click again to confirm").withStyle(ChatFormatting.YELLOW);

    private static InventoryButton hoveredButton = null;
    private static long hoveredSince = 0;

    private InventoryButtons() {}

    public record Placement(InventoryButton button, int x, int y) {
        public boolean contains(double mouseX, double mouseY) {
            int size = button.size();
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;

            // drawing happens in InventoryButtonRenderMixin - the screen events fire after the
            // tooltip pass, so a button drawn there would sit on top of the tooltip
            ScreenMouseEvents.allowMouseClick(screen).register(
                    (ignored, event) -> allowMouse(container, event, 0));
            ScreenMouseEvents.allowMouseRelease(screen).register(
                    (ignored, event) -> allowMouse(container, event, 1));
        });
    }

    // --- render ---

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                              int mouseX, int mouseY) {
        // this runs for every container screen, every frame - leave before allocating anything
        if (!TeslaMapsConfig.get().inventoryButtons) return;

        Minecraft client = Minecraft.getInstance();
        InventoryButton hovered = null;

        for (Placement placement : placements(screen)) {
            draw(graphics, placement.button(), placement.x(), placement.y(), OPAQUE);

            if (placement.contains(mouseX, mouseY)) hovered = placement.button();
        }

        if (hovered == null) {
            hoveredButton = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (hoveredButton != hovered) {
            hoveredButton = hovered;
            hoveredSince = now;
        }
        if (awaitingConfirm(hovered)) {
            graphics.setTooltipForNextFrame(client.font, List.of(CONFIRM_TEXT.getVisualOrderText()),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
            return;
        }
        if (now - hoveredSince <= TeslaMapsConfig.get().invButtonTooltipDelay) return;

        // buttons may sit over a slot, so push the slot's own tooltip aside
        Component text = Component.literal(commandWithSlash(hovered)).withStyle(ChatFormatting.GRAY);
        graphics.setTooltipForNextFrame(client.font, List.of(text.getVisualOrderText()),
                DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
    }

    /** Frame and icon at x/y, scaled by the button's own size. */
    public static void draw(GuiGraphicsExtractor graphics, InventoryButton button, int x, int y, int color) {
        float s = button.scaleOrOne();
        if (s == 1f) {
            ButtonTexture.drawButton(graphics, button.backgroundIndex, x, y, color);
            ButtonIcons.render(graphics, button.iconOrEmpty(), x + 1, y + 1);
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        ButtonTexture.drawButton(graphics, button.backgroundIndex, 0, 0, color);
        ButtonIcons.render(graphics, button.iconOrEmpty(), 1, 1);
        pose.popMatrix();
    }

    // --- input ---

    /** when: 0 = mouse down, 1 = mouse up. */
    private static boolean allowMouse(AbstractContainerScreen<?> screen, MouseButtonEvent event, int when) {
        Placement placement = findAt(screen, event.x(), event.y());
        if (placement == null) return true;

        // holding an item: swallow the click so neither the command nor the slot below fires
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !screen.getMenu().getCarried().isEmpty()) return false;

        // only one of down/up runs it; the other passes through so slot dragging still works
        if (TeslaMapsConfig.get().invButtonClickType != when) return true;

        if (needsConfirm(placement.button())) {
            long now = System.currentTimeMillis();
            if (confirmButton != placement.button() || now - confirmAt >= CONFIRM_MS) {
                confirmButton = placement.button();
                confirmAt = now;
                client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
                return false;
            }
            confirmButton = null;
        }

        runCommand(placement.button());
        return false;
    }

    private static final long CONFIRM_MS = 1000;
    private static InventoryButton confirmButton = null;
    private static long confirmAt = 0;

    public static final String[] CONFIRM_MODES = {"Off", "Warp Commands", "All Buttons"};

    private static boolean needsConfirm(InventoryButton button) {
        String mode = TeslaMapsConfig.get().invButtonConfirm;
        if ("All Buttons".equals(mode)) return true;
        if (!"Warp Commands".equals(mode)) return false;
        String cmd = button.commandOrEmpty().trim().toLowerCase(java.util.Locale.ROOT);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        return cmd.startsWith("warp") || cmd.equals("hub") || cmd.equals("is") || cmd.startsWith("play ");
    }

    private static boolean awaitingConfirm(InventoryButton button) {
        return confirmButton == button && System.currentTimeMillis() - confirmAt < CONFIRM_MS;
    }

    private static void runCommand(InventoryButton button) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String command = button.commandOrEmpty().trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return;

        client.player.connection.sendCommand(command);
    }

    // --- placement ---

    public static List<Placement> placements(AbstractContainerScreen<?> screen) {
        List<Placement> placements = new ArrayList<>();
        forEachPlacement(screen, placements::add);
        return placements;
    }

    private static void forEachPlacement(AbstractContainerScreen<?> screen, Consumer<Placement> consumer) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.section("Inventory") || !config.inventoryButtons) return;
        if (!SkyblockUtils.isInSkyblock()) return;
        if (config.invButtonsHideInDungeonMenus && isDungeonMenu(screen)) return;

        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int left = accessor.getX();
        int top = accessor.getY();
        int width = accessor.getImageWidth();
        int height = accessor.getImageHeight();

        for (InventoryButton button : config.invButtons) {
            if (button == null || !button.isActive()) continue;
            if (button.playerInvOnly && !(screen instanceof InventoryScreen)) continue;

            consumer.accept(new Placement(button,
                    left + button.x + (button.anchorRight ? width : 0),
                    top + button.y + (button.anchorBottom ? height : 0)));
        }
    }

    private static Placement findAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Placement placement : placements(screen)) {
            if (placement.contains(mouseX, mouseY)) return placement;
        }
        return null;
    }

    private static boolean isDungeonMenu(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        if (DUNGEON_MENUS.contains(title)) return true;
        for (String prefix : DUNGEON_MENU_PREFIXES) {
            if (title.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String commandWithSlash(InventoryButton button) {
        String command = button.commandOrEmpty().trim();
        return command.startsWith("/") ? command : "/" + command;
    }
}
