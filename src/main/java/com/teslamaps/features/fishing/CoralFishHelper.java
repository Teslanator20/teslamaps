package com.teslamaps.features.fishing;

// Ported from SkyHanni's CoralFishHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CoralFishHelper {

    private record Row(String name, double price, String text) {}

    private static final Pattern MENU = Pattern.compile("^\\(\\d+/\\d+\\) Fish Family$");
    private static final Pattern NAME = Pattern.compile("^\\w+ the Fish$");
    private static final Pattern SHOWN = Pattern.compile("Fish Shown: (\\d+)/(\\d+)");
    private static final int ROW_H = 11, VISIBLE = 18;

    private static final List<Row> rows = new ArrayList<>();
    private static final List<Row> shown = new ArrayList<>();
    private static int found = -1, total;
    private static long builtAt, lastClick;
    private static int scroll;
    private static int listX, listY, listW;
    private static String lastQuery = "";

    private CoralFishHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CoralFishHelper").section("Fishing")
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Coral Fish");
                    b.toggle("Coral Fish Helper", () -> c.coralFishHelper, v -> c.coralFishHelper = v)
                            .desc("In Galatea's \"Fish Family\" menu: list of the fish you still miss, cheapest lowest BIN first. "
                                    + "Click a row for /ahs, scroll with the wheel, the container search bar filters the list.")
                            .tags("coral", "fish family", "galatea", "the fish", "lowest bin");
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!MENU.matcher(StringUtil.stripTrim(screen.getTitle().getString())).matches()) return;
            builtAt = 0;
            scroll = 0;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (!active()) return;
                if (System.currentTimeMillis() - builtAt > 500) build(cs.getMenu());
                draw(cs, g, mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !active() || e.button() != 0 || !click(e.x(), e.y()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hs, vs) -> !active() || !onScroll(mx, my, vs));
        });
    }

    private static boolean active() {
        return cfg().coralFishHelper && cfg().section("Fishing");
    }

    private static void build(AbstractContainerMenu menu) {
        builtAt = System.currentTimeMillis();
        rows.clear();
        found = -1;
        int size = menu.slots.size() - 36;
        if (size <= 4) return;
        for (String l : ItemUtil.loreLines(menu.getSlot(4).getItem())) {
            Matcher m = SHOWN.matcher(l);
            if (m.find()) {
                found = Integer.parseInt(m.group(1));
                total = Integer.parseInt(m.group(2));
            }
        }
        for (int i = 0; i < size; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (!st.is(Items.GRAY_DYE)) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (!NAME.matcher(name).matches()) continue;
            String id = SkyblockItems.idForName(name);
            Double p = id == null ? null : PriceManager.lowestBinOrNull(id);
            double price = p == null ? Double.MAX_VALUE : p;
            rows.add(new Row(name, price, "§a" + name + " " + (p == null ? "§cno price" : "§6" + ItemPrice.format(p))));
        }
        rows.sort((a, b) -> Double.compare(a.price, b.price));
        lastQuery = null;
    }

    private static void filter() {
        String q = com.teslamaps.features.Searchbar.query().toLowerCase(Locale.ROOT);
        if (q.equals(lastQuery)) return;
        lastQuery = q;
        shown.clear();
        for (Row r : rows) if (q.isEmpty() || r.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(r);
        scroll = Math.max(0, Math.min(scroll, shown.size() - VISIBLE));
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (found < 0) { listW = 0; return; }
        filter();
        Font font = Minecraft.getInstance().font;
        HandledScreenAccessor a = (HandledScreenAccessor) cs;
        int left = a.getX();
        listW = Math.min(170, left - 10);
        if (listW < 80) { listW = 0; return; }
        listX = left - listW - 6;
        listY = a.getY();
        boolean done = found >= total;
        int n = done ? 0 : Math.min(VISIBLE, shown.size());
        HudStyle.screenPanel(g, listX, listY, listW, 28 + n * ROW_H + (shown.size() > VISIBLE ? 10 : 0), 4);
        g.text(font, "§dCoral Fish Helper", listX + 4, listY + 4, 0xFFFFFFFF);
        g.text(font, done ? "§a§lAll fish found!" : "§7Found §a" + found + "§7/§a" + total, listX + 4, listY + 15, 0xFFFFFFFF);
        for (int i = 0; i < n; i++) {
            Row r = shown.get(scroll + i);
            int y = listY + 27 + i * ROW_H;
            if (mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H) g.fill(listX + 2, y, listX + listW - 2, y + ROW_H, 0x30FFFFFF);
            String t = r.text;
            while (t.length() > 1 && font.width(t) > listW - 8) t = t.substring(0, t.length() - 1);
            g.text(font, t, listX + 4, y + 1, 0xFFFFFFFF);
        }
        if (!done && shown.size() > VISIBLE) {
            g.text(font, "§8" + (scroll + 1) + "-" + (scroll + n) + " of " + shown.size(), listX + 4, listY + 28 + n * ROW_H, 0xFFFFFFFF);
        }
    }

    private static boolean inside(double mx, double my) {
        return listW > 0 && mx >= listX && mx < listX + listW && my >= listY;
    }

    private static boolean onScroll(double mx, double my, double v) {
        if (!inside(mx, my)) return false;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(v), shown.size() - VISIBLE));
        return true;
    }

    private static boolean click(double mx, double my) {
        if (!inside(mx, my) || found >= total) return false;
        int i = (int) Math.floor((my - listY - 27) / ROW_H);
        if (my < listY + 27 || i < 0 || i >= Math.min(VISIBLE, shown.size())) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 500) return true;
        lastClick = now;
        Minecraft mc = Minecraft.getInstance();
        // packet, not sendCommand: the client /ahs would catch it
        if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket("ahs " + shown.get(scroll + i).name));
        return true;
    }
}
