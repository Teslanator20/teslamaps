package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.HashMap;
import java.util.Map;

public class SlotBinds {

    public static final String[] PROFILES = {
            "Profile 1", "Profile 2", "Profile 3", "Profile 4", "Profile 5", "Profile 6"
    };
    public static final String[] LINE_MODES = {"Hover", "On Hover + Shift", "None"};

    private static Integer previousSlot = null;

    private static String profile() {
        String p = TeslaMapsConfig.get().slotBindsProfile;
        for (String s : PROFILES) if (s.equals(p)) return s;
        return PROFILES[0];
    }

    private static Map<Integer, Integer> binds() {
        return TeslaMapsConfig.get().slotBindsData.computeIfAbsent(profile(), k -> new HashMap<>());
    }

    private static Integer hoveredIndex(InventoryScreen screen) {
        Slot slot = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (slot == null) return null;
        return slot.index >= 5 && slot.index < 45 ? slot.index : null;
    }

    private static boolean inHotbar(int index) {
        return index >= 36 && index <= 44;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    public static boolean onSlotClick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.slotBinds || !mc.hasShiftDown()) return false;
        if (!(mc.screen instanceof InventoryScreen screen)) return false;
        if (mc.player == null || mc.gameMode == null) return false;

        Integer clicked = hoveredIndex(screen);
        if (clicked == null) return false;
        Integer bound = binds().get(clicked);
        if (bound == null) return false;

        int from, to;
        if (inHotbar(clicked)) { from = bound; to = clicked; }
        else if (inHotbar(bound)) { from = clicked; to = bound; }
        else return false;

        mc.gameMode.handleContainerInput(screen.getMenu().containerId, from, to % 36, ContainerInput.SWAP, mc.player);
        return true;
    }

    public static boolean onKey(int key) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.slotBinds || c.slotBindsKey == -1 || key != c.slotBindsKey) return false;
        if (!(mc.screen instanceof InventoryScreen screen)) return false;

        Integer clicked = hoveredIndex(screen);
        if (clicked == null) return false;

        if (previousSlot != null) {
            int slot = previousSlot;
            if (slot == clicked) {
                msg("§cYou can't bind a slot to itself.");
                return true;
            }
            if (!inHotbar(slot) && !inHotbar(clicked)) {
                msg("§cOne of the slots must be in the hotbar (36–44).");
                return true;
            }
            msg("§aAdded bind from slot §b" + slot + " §ato §d" + clicked + " §7(" + profile() + ").");
            binds().put(slot, clicked);
            TeslaMapsConfig.save();
            previousSlot = null;
            return true;
        }

        Integer bound = binds().remove(clicked);
        if (bound != null) {
            TeslaMapsConfig.save();
            msg("§cRemoved bind from slot §b" + clicked + " §cto §d" + bound + " §7(" + profile() + ").");
            return true;
        }
        previousSlot = clicked;
        return true;
    }

    public static void renderLine(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotBinds) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof InventoryScreen screen)) return;

        Integer hovered = hoveredIndex(screen);
        if (hovered == null) return;
        Integer bound = binds().get(hovered);

        HandledScreenAccessor acc = (HandledScreenAccessor) screen;
        Slot start = screen.getMenu().getSlot(previousSlot != null ? previousSlot : hovered);
        float startX = start.x + acc.getX() + 8;
        float startY = start.y + acc.getY() + 8;

        float endX, endY;
        if (previousSlot != null) {
            endX = mouseX;
            endY = mouseY;
        } else if (bound != null) {
            Slot end = screen.getMenu().getSlot(bound);
            endX = end.x + acc.getX() + 8;
            endY = end.y + acc.getY() + 8;
        } else return;

        boolean draw = switch (lineMode(c.slotBindsLineDisplay)) {
            case 0 -> previousSlot != null || bound != null;
            case 1 -> previousSlot != null || (bound != null && mc.hasShiftDown());
            case 2 -> previousSlot != null;
            default -> false;
        };
        if (!draw) return;

        drawLine(ctx, startX, startY, endX, endY,
                TeslaMapsConfig.parseColor(c.colorSlotBinds), c.slotBindsLineWidth);
    }

    public static void onScreenClose() {
        previousSlot = null;
    }

    private static int lineMode(String mode) {
        for (int i = 0; i < LINE_MODES.length; i++) if (LINE_MODES[i].equals(mode)) return i;
        return 0;
    }

    private static void drawLine(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, int color, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);

        ctx.pose().pushMatrix();
        ctx.pose().translate(x1, y1);
        ctx.pose().rotate((float) Math.atan2(dy, dx));
        ctx.pose().scale(1f, width);

        ctx.fill(0, -1, (int) Math.ceil(length), 1, color);

        ctx.pose().popMatrix();
    }
}
