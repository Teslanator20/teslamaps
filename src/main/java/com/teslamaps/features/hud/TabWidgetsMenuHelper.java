package com.teslamaps.features.hud;

// GUI-023, after Skyblocker's WidgetsListScreen: live tab preview + overflow warning + widget list beside /widgets
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TabWidgetsMenuHelper {

    private TabWidgetsMenuHelper() {}

    private record Row(int slot, String name, int state) {}

    private static final int ROW_H = 11;
    private static final int COL_W = 120;

    @SuppressWarnings("unchecked")
    private static final List<FormattedCharSequence>[] columns = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private static final List<Row> rows = new ArrayList<>();
    private static boolean preview, overflowing;
    private static long builtAt, lastClick;
    private static int listX, listY, listW;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TabWidgetsMenuHelper")
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("/widgets Menu Helper", () -> c.widgetsHelper, v -> c.widgetsHelper = v)
                            .desc("In Hypixel's Tablist Widgets menu: live preview of the three tab columns next to the chest, "
                                    + "a red warning when the widgets no longer fit, and a clickable list of the widgets.")
                            .tags("widget", "tablist", "tab list", "preview");
                    b.toggle("  Widget List", () -> c.widgetsHelperList, v -> c.widgetsHelperList = v)
                            .desc("Rows left of the chest; a click clicks the real slot. Green = on, red = off.");
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs) || !(cs.getMenu() instanceof ChestMenu)) return;
            String title = StringUtil.strip(screen.getTitle().getString()).trim().toLowerCase(Locale.ROOT);
            if (!title.startsWith("tablist widgets") && !title.startsWith("widgets on") && !title.startsWith("widgets in")) return;
            boolean top = title.startsWith("tablist widgets");
            builtAt = 0;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (!cfg().widgetsHelper) return;
                if (System.currentTimeMillis() - builtAt > 250) build(cs.getMenu(), top);
                draw(cs, g, mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) ->
                    !cfg().widgetsHelper || !cfg().widgetsHelperList || e.button() != 0 || !click(cs, e.x(), e.y()));
        });
    }

    private static void build(AbstractContainerMenu menu, boolean top) {
        builtAt = System.currentTimeMillis();
        preview = false;
        overflowing = false;
        for (List<FormattedCharSequence> c : columns) c.clear();
        rows.clear();
        int size = menu.slots.size() - 36;
        if (size < 54) return;
        for (int i = 0; i < 3; i++) {
            ItemStack st = menu.getSlot(3 + i).getItem();
            if (!StringUtil.strip(st.getHoverName().getString()).endsWith("Widgets Preview")) continue;
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore == null) continue;
            preview = true;
            List<Boolean> blank = new ArrayList<>();
            for (Component line : lore.lines()) {
                String t = line.getString();
                if (!t.startsWith("⬛")) continue;
                columns[i].add(ComponentRenderUtils.clipText(line, Minecraft.getInstance().font, COL_W));
                blank.add(t.substring(t.indexOf('⬛') + 1).isBlank());
            }
            if (i == 2) overflowing = blank.size() >= 2 && !(blank.getLast() && blank.get(blank.size() - 2));
        }
        int from = top ? 10 : 19, to = size - 9;
        for (int slot = from; slot < to; slot++) {
            ItemStack st = menu.getSlot(slot).getItem();
            if (st.isEmpty() || st.is(Items.STAINED_GLASS_PANE.black())) continue;
            String name = StringUtil.strip(st.getHoverName().getString()).trim();
            if (name.isEmpty()) continue;
            ItemLore lore = st.get(DataComponents.LORE);
            String last = lore == null || lore.lines().isEmpty() ? ""
                    : StringUtil.strip(lore.lines().getLast().getString()).toLowerCase(Locale.ROOT);
            int state = last.contains("disable") ? 1 : last.contains("enable") ? -1 : 0;
            rows.add(new Row(slot, name, state));
        }
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        HandledScreenAccessor a = (HandledScreenAccessor) cs;
        int left = a.getX(), topY = a.getY(), right = left + a.getImageWidth();
        if (preview) {
            int lines = 0;
            for (List<FormattedCharSequence> c : columns) lines = Math.max(lines, c.size());
            int tw = COL_W * 3 + 16, th = lines * 9 + 18 + (overflowing ? 20 : 0);
            int avail = cs.width - right - 10;
            float scale = Math.max(0.4f, Math.min(1f, avail / (float) tw));
            g.pose().pushMatrix();
            g.pose().translate(right + 6, topY);
            g.pose().scale(scale, scale);
            HudStyle.screenPanel(g, 0, 0, tw, th, 4);
            g.text(font, "§fTab Preview", 4, 4, 0xFFFFFFFF);
            for (int i = 0; i < 3; i++) {
                int cx = 4 + i * (COL_W + 4);
                g.fill(cx, 16, cx + COL_W, 16 + lines * 9, 0x20FFFFFF);
                List<FormattedCharSequence> col = columns[i];
                for (int j = 0; j < col.size(); j++) g.text(font, col.get(j), cx, 16 + j * 9, 0xFFFFFFFF);
            }
            if (overflowing) {
                g.text(font, "§cThe widgets do not fit into the tab list!", 4, th - 18, 0xFFFFFFFF);
                g.text(font, "§7Turn widgets off or enable the third column.", 4, th - 9, 0xFFFFFFFF);
            }
            g.pose().popMatrix();
        }
        if (!cfg().widgetsHelperList || rows.isEmpty()) { listW = 0; return; }
        listW = Math.min(150, left - 10);
        if (listW < 60) { listW = 0; return; }
        listX = left - listW - 6;
        listY = topY;
        int h = rows.size() * ROW_H + 16;
        HudStyle.screenPanel(g, listX, listY, listW, h, 4);
        g.text(font, "§fWidgets", listX + 4, listY + 4, 0xFFFFFFFF);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = listY + 14 + i * ROW_H;
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H;
            if (hover) g.fill(listX + 2, y, listX + listW - 2, y + ROW_H, 0x30FFFFFF);
            int color = r.state > 0 ? 0xFF55FF55 : r.state < 0 ? 0xFFFF5555 : 0xFFDDDDDD;
            String t = r.name;
            while (t.length() > 1 && font.width(t) > listW - 8) t = t.substring(0, t.length() - 1);
            g.text(font, t, listX + 4, y + 1, color);
        }
    }

    private static boolean click(AbstractContainerScreen<?> cs, double mx, double my) {
        if (listW == 0 || mx < listX || mx >= listX + listW) return false;
        int i = (int) Math.floor((my - listY - 14) / ROW_H);
        if (my < listY + 14 || i < 0 || i >= rows.size()) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 250) return true;
        lastClick = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return true;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, rows.get(i).slot, 0, ContainerInput.PICKUP, mc.player);
        builtAt = 0;
        return true;
    }
}
