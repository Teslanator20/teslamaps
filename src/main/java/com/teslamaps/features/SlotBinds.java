package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
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
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String p = c.slotBindsProfile;
        if (c.slotBindsAreaSwitch && c.slotBindsAreaProfiles != null) {
            String a = c.slotBindsAreaProfiles.get(com.teslamaps.utils.SkyblockUtils.getArea());
            if (a != null) p = a;
        }
        for (String s : PROFILES) if (s.equals(p)) return s;
        return PROFILES[0];
    }

    private static String dataKey() {
        String p = profile();
        if (!TeslaMapsConfig.get().slotBindsPerProfile) return p;
        String sb = com.teslamaps.utils.SbProfile.key();
        return sb == null ? p : "sb:" + sb + ":" + p;
    }

    private static Map<Integer, Integer> binds() {
        Map<String, Map<Integer, Integer>> all = TeslaMapsConfig.get().slotBindsData;
        String key = dataKey();
        Map<Integer, Integer> m = all.get(key);
        if (m == null) {
            m = new HashMap<>();
            // a new skyblock profile starts from the shared set
            Map<Integer, Integer> base = key.equals(profile()) ? null : all.get(profile());
            if (base != null) m.putAll(base);
            all.put(key, m);
        }
        return m;
    }

    public static void bindCurrentArea() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String area = com.teslamaps.utils.SkyblockUtils.getArea();
        if (area.isEmpty()) { msg("§cNo area in the tab list."); return; }
        c.slotBindsAreaProfiles.put(area, c.slotBindsProfile);
        TeslaMapsConfig.save();
        msg("§a" + area + " §7now uses §b" + c.slotBindsProfile + "§7.");
    }

    public static void clearAreas() {
        TeslaMapsConfig.get().slotBindsAreaProfiles.clear();
        TeslaMapsConfig.save();
        msg("§cCleared all area profiles.");
    }

    private static AbstractContainerScreen<?> screen() {
        Screen s = Minecraft.getInstance().screen;
        if (s instanceof InventoryScreen inv) return inv;
        if (!TeslaMapsConfig.get().slotBindsAllContainers) return null;
        if (!(s instanceof AbstractContainerScreen<?> cs) || StorageOverlay.active()) return null;
        return cs;
    }

    // binds are stored as InventoryMenu indices (5-8 armor, 9-35 main, 36-44 hotbar)
    private static int toKey(int containerSlot) {
        if (containerSlot < 9) return 36 + containerSlot;
        if (containerSlot < 36) return containerSlot;
        if (containerSlot < 40) return 5 + (39 - containerSlot);
        return -1;
    }

    private static int toContainerSlot(int key) {
        if (key >= 36) return key - 36;
        if (key >= 9) return key;
        return 39 - (key - 5);
    }

    private static Slot slotFor(AbstractContainerScreen<?> screen, int key) {
        if (screen instanceof InventoryScreen) return screen.getMenu().getSlot(key);
        int cs = toContainerSlot(key);
        for (Slot s : screen.getMenu().slots) if (s.container instanceof Inventory && s.getContainerSlot() == cs) return s;
        return null;
    }

    private static Integer hoveredIndex(AbstractContainerScreen<?> screen) {
        Slot slot = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (slot == null) return null;
        if (screen instanceof InventoryScreen) return slot.index >= 5 && slot.index < 45 ? slot.index : null;
        if (!(slot.container instanceof Inventory)) return null;
        int k = toKey(slot.getContainerSlot());
        return k >= 5 && k < 45 ? k : null;
    }

    private static boolean isMenuItem(AbstractContainerScreen<?> screen, int key) {
        Slot s = slotFor(screen, key);
        return s != null && com.teslamaps.utils.StringUtil.stripTrim(s.getItem().getHoverName().getString()).startsWith("SkyBlock Menu");
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
        AbstractContainerScreen<?> screen = screen();
        if (screen == null) return false;
        if (mc.player == null || mc.gameMode == null) return false;

        Integer clicked = hoveredIndex(screen);
        if (clicked == null) return false;
        Integer bound = binds().get(clicked);
        if (bound == null) return false;

        int from, to;
        if (inHotbar(clicked)) { from = bound; to = clicked; }
        else if (inHotbar(bound)) { from = clicked; to = bound; }
        else return false;

        Slot fromSlot = slotFor(screen, from);
        if (fromSlot == null) return false;
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, fromSlot.index, to % 36, ContainerInput.SWAP, mc.player);
        return true;
    }

    public static boolean onKey(int key) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.slotBinds || c.slotBindsKey == -1 || key != c.slotBindsKey) return false;
        AbstractContainerScreen<?> screen = screen();
        if (screen == null) return false;

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
            if (isMenuItem(screen, slot) || isMenuItem(screen, clicked)) {
                msg("§cThe SkyBlock Menu slot can't be bound.");
                previousSlot = null;
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
        AbstractContainerScreen<?> screen = screen();
        if (screen == null) return;

        Integer hovered = hoveredIndex(screen);
        if (hovered == null) return;
        Integer bound = binds().get(hovered);

        HandledScreenAccessor acc = (HandledScreenAccessor) screen;
        Slot start = slotFor(screen, previousSlot != null ? previousSlot : hovered);
        if (start == null) return;
        float startX = start.x + acc.getX() + 8;
        float startY = start.y + acc.getY() + 8;

        float endX, endY;
        if (previousSlot != null) {
            endX = mouseX;
            endY = mouseY;
        } else if (bound != null) {
            Slot end = slotFor(screen, bound);
            if (end == null) return;
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
