package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The estimated item value breakdown for the hovered slot, drawn as the plain
 * line list SkyHanni shows.
 */
public class ItemValueOverlay {

    public static final int LINE_HEIGHT = 12;

    /** What the HUD editor shows in place of a real hovered item. */
    public static final List<String> SAMPLE = List.of(
            "§aEstimated Item Value:",
            "§7Base item: §fHyperion §6(1.2B)",
            "§7Reforge: §9Withered",
            "§7Stars: §a5§7/10",
            "§aTotal: §6§l1.4B coins",
            "§7Lowest BIN: §61.3B");

    /**
     * Keyed by slot index, not by the stack. The equipped armor slots hand back a fresh ItemStack
     * on every call, so an identity-keyed cache missed every frame and the hover tracking below
     * restarted its timer every frame - which is why the panel never appeared over worn armor.
     */
    private record Cached(ItemStack stack, List<String> lines) {}

    private static final Map<Integer, Cached> cache = new java.util.HashMap<>();
    private static Screen lastScreen;
    private static int hoveredSlot = -1;
    private static long hoverStart;
    /** the delay is paid once per inventory session, not per item, same as SkyHanni */
    private static boolean delayOver;
    private static long lastActive;

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().itemValueGui) return false;
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        long now = System.currentTimeMillis();
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            cache.clear();
            hoveredSlot = -1;
            // an auction page flip reopens the container, that is the same session
            if (now - lastActive > 1500) delayOver = false;
        }
        lastActive = now;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot slot = acc.getFocusedSlot();
        if (slot == null || slot.getItem().isEmpty()) {
            hoveredSlot = -1;
            return;
        }

        ItemStack item = slot.getItem();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean keyHeld = cfg.itemValueRequireKey && com.teslamaps.utils.KeyNames.isDown(cfg.itemValueKey);
        if (cfg.itemValueRequireKey && !keyHeld) return;
        if (cfg.itemValueSkipMenus && skipMenuItem(cs, slot)) return;
        if (slot.index != hoveredSlot) {
            hoveredSlot = slot.index;
            hoverStart = now;
        }

        int delay = TeslaMapsConfig.get().itemValueHoverDelay;
        if (!delayOver && !keyHeld) {
            if (delay > 0 && now - hoverStart < delay) return;
            delayOver = true;
        }

        Cached known = cache.get(slot.index);
        List<String> lines;
        if (known != null && ItemStack.matches(known.stack(), item)) {
            lines = known.lines();
        } else {
            lines = EstimatedValue.display(item);
            cache.put(slot.index, new Cached(item.copy(), lines));
        }
        if (lines.isEmpty()) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.itemValueFixedPos) {
            drawAt(ctx, mc, lines, c.itemValueX, c.itemValueY, c.itemValueScale);
            return;
        }
        draw(ctx, mc, lines, acc.getX(), acc.getY(), acc.getImageWidth(), c.itemValueScale);
    }

    // SkyHanni's extra ignores: museum armor sets, island delivery rewards, wardrobe slot picker
    private static boolean skipMenuItem(AbstractContainerScreen<?> cs, Slot slot) {
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return false;
        String title = com.teslamaps.utils.StringUtil.stripTrim(cs.getTitle().getString());
        if (title.equals("Choose a wardrobe slot")) return true;
        if (title.startsWith("Museum ")) return com.teslamaps.utils.ItemUtil.loreContains(slot.getItem(), "Armor Set");
        if (title.equals("Island Deliveries")) return com.teslamaps.utils.ItemUtil.loreContains(slot.getItem(), "Click to collect!");
        return false;
    }

    // left of the inventory GUI, flipped to the right when there is no room
    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, List<String> lines,
                             int guiX, int guiY, int guiW, float scale) {
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.font.width(line));

        int scaledW = Math.round(width * scale);
        int x = guiX - scaledW - 8;
        if (x < 2) x = guiX + guiW + 8;
        int y = guiY;
        int height = Math.round(lines.size() * LINE_HEIGHT * scale);
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (y + height > screenH - 2) y = Math.max(2, screenH - 2 - height);

        drawAt(ctx, mc, lines, x, y, scale);
    }

    /** Fixed screen position, used once the panel has been dragged in the HUD editor. */
    public static void drawAt(GuiGraphicsExtractor ctx, Minecraft mc, List<String> lines,
                              int x, int y, float scale) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int ly = 0;
        for (String line : lines) {
            ctx.text(mc.font, line, 0, ly, 0xFFFFFFFF);
            ly += LINE_HEIGHT;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        int w = 0;
        for (String line : SAMPLE) w = Math.max(w, font.width(line));
        return new int[]{ w, SAMPLE.size() * LINE_HEIGHT };
    }

    private ItemValueOverlay() {}
}
