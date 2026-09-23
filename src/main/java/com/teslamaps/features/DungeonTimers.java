package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonTimers {

    public static final String SAMPLE_TEXT = "Warp: 30.00s"; // for HUD-editor sizing
    public record Row(String text, ItemStack icon, int marker) {
        public Row(String text, ItemStack icon) { this(text, icon, 0); }
    }
    private static final Map<String, Row> lines = new LinkedHashMap<>();

    // the Bonzo/Spirit/Phoenix masks render as their own movable block
    private static final Set<String> INVINCIBILITY_KEYS = Set.of("bonzo", "spirit", "phoenix");

    private static final float ICON = 0.625f; // 16px -> 10px, ~text height

    public static void set(String key, String text) { lines.put(key, new Row(text, null)); }
    public static void set(String key, String text, ItemStack icon) { lines.put(key, new Row(text, icon)); }
    public static void set(String key, String text, ItemStack icon, int marker) { lines.put(key, new Row(text, icon, marker)); }
    public static void clear(String key) { lines.remove(key); }
    public static void clearAll() { lines.clear(); }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();

        // the invincibility block stays up in the inventory so it can be dragged there.
        // Chat does not count as a screen, the timers have to survive typing a message.
        boolean inScreen = mc.screen != null
                && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        if (inScreen && !inInventory(mc)) return;

        List<Row> inv = new ArrayList<>();
        List<Row> rest = new ArrayList<>();
        for (Map.Entry<String, Row> e : lines.entrySet()) {
            (INVINCIBILITY_KEYS.contains(e.getKey()) ? inv : rest).add(e.getValue());
        }
        if (!inScreen && !rest.isEmpty()) {
            drawRows(ctx, mc, c.dungeonTimersX, c.dungeonTimersY, c.dungeonTimersScale, rest);
        }
        if (!inv.isEmpty()) drawRows(ctx, mc, c.invincibilityX, c.invincibilityY, c.invincibilityScale, inv);
    }

    private static boolean inInventory(Minecraft mc) {
        return mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    public static boolean invincibilityVisible() {
        if (!TeslaMapsConfig.get().invincibilityTimer) return false;
        for (String key : INVINCIBILITY_KEYS) if (lines.containsKey(key)) return true;
        return false;
    }

    public static int invincibilityWidth(float scale) {
        Minecraft mc = Minecraft.getInstance();
        int widest = 0;
        for (Map.Entry<String, Row> e : lines.entrySet()) {
            if (!INVINCIBILITY_KEYS.contains(e.getKey())) continue;
            Row r = e.getValue();
            boolean icon = r.icon() != null && !r.icon().isEmpty();
            widest = Math.max(widest, (icon ? 12 : 0) + mc.font.width(r.text()));
        }
        return (int) (widest * scale);
    }

    public static int invincibilityHeight(float scale) {
        int rows = 0;
        for (String key : lines.keySet()) if (INVINCIBILITY_KEYS.contains(key)) rows++;
        return (int) ((rows * 11 - 2) * scale);
    }

    // HUD-editor preview: the warp/purple/etc block
    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        drawRows(ctx, mc, x, y, scale, List.of(
                new Row("§eWarp§f: §c30.00s", null), new Row("§dPurple§f: §d2.10s", null)));
    }

    // HUD-editor preview: the invincibility (skull) block
    public static void drawInvincibility(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        drawRows(ctx, mc, x, y, scale, List.of(
                new Row("§a§lREADY", com.teslamaps.features.TimerTriggers.maskIcon(0)),
                new Row("§c30.00s", com.teslamaps.features.TimerTriggers.maskIcon(1)),
                new Row("§a2.50s", com.teslamaps.features.TimerTriggers.maskIcon(2))));
    }

    private static void drawRows(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, Iterable<Row> rows) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);

        int widest = 0;
        int lines = 0;
        for (Row r : rows) {
            boolean icon = r.icon() != null && !r.icon().isEmpty();
            widest = Math.max(widest, (icon ? 12 : 0) + mc.font.width(r.text()));
            lines++;
        }
        com.teslamaps.render.gui.TeslaGui.backdrop(ctx, 0, 0, widest, lines * 11 - 2);

        int row = 0;
        for (Row r : rows) {
            if (r.icon() != null && !r.icon().isEmpty()) {
                pose.pushMatrix();
                pose.translate(0, row);
                pose.scale(ICON, ICON);
                ctx.item(r.icon(), 0, 0);
                pose.popMatrix();
                if (r.marker() != 0) ctx.fill(10, row, 11, row + 10, r.marker());
                ctx.text(mc.font, r.text(), 12, row + 1, 0xFFFFFFFF);
            } else {
                ctx.text(mc.font, r.text(), 0, row, 0xFFFFFFFF);
            }
            row += 11;
        }
        pose.popMatrix();
    }
}
