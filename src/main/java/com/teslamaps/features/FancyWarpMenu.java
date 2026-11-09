package com.teslamaps.features;

// Idea from SkyblockAddons' IslandWarpGui: the Fast Travel menu drawn as an island map, click = click the island
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FancyWarpMenu {

    private static final int COLS = 6, ROWS = 4, BAR_H = 18, BTN_W = 70, GAP = 6;

    // needle, col, row, color; first match wins
    private static final Object[][] ISLANDS = {
            {"dungeon", 5, 1, 0xFF555566}, {"the end", 0, 0, 0xFF3B2B5A}, {"crimson", 1, 0, 0xFF8A2A1E},
            {"private island", 3, 0, 0xFF3E8E3E}, {"garden", 4, 0, 0xFF4CAF50}, {"jerry", 5, 0, 0xFFB8D4E8},
            {"galatea", 0, 1, 0xFF2E7D6B}, {"spider", 1, 1, 0xFF5A4A3A}, {"hub", 2, 1, 0xFF6AAE4C},
            {"barn", 4, 1, 0xFFC8A34A}, {"farming", 4, 1, 0xFFC8A34A}, {"park", 1, 2, 0xFF2F6B3A},
            {"gold mine", 3, 2, 0xFFD4AF37}, {"bayou", 4, 2, 0xFF4A6B3A}, {"rift", 5, 2, 0xFF9C27B0},
            {"deep caverns", 3, 3, 0xFF607080}, {"dwarven", 2, 3, 0xFF7A6A5A}, {"crystal", 1, 3, 0xFFB06AD0},
            {"mineshaft", 0, 3, 0xFF505050}};

    private static final class Island {
        int slot, col, row, color;
        ItemStack stack;
        String name, sub;
        boolean locked;
    }

    private static final class Button {
        int slot;
        String label;
    }

    private static List<Island> islands = List.of();
    private static List<Button> buttons = List.of();
    private static int containerId = -1, vanillaFor = -1;
    private static long lastClick;

    private FancyWarpMenu() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("FancyWarpMenu").section("Inventory").when(() -> cfg().fancyWarpMenu);

    public static void register() {
        FEATURE.tick(FancyWarpMenu::tick)
                .config("Inventory", b -> {
                    b.label("Fast Travel");
                    b.toggle("Fancy Warp Menu", () -> cfg().fancyWarpMenu, v -> cfg().fancyWarpMenu = v)
                            .desc("Draws the Fast Travel menu as an island map. Left click warps, right click opens that island's warps");
                });
    }

    private static boolean pane(ItemStack s) {
        return s.isEmpty() || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("stained_glass_pane");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Fast Travel")) {
            containerId = -1;
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) vanillaFor = -1;
            return;
        }
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() < 54) return;
        List<Island> out = new ArrayList<>();
        List<Button> btns = new ArrayList<>();
        boolean[] used = new boolean[COLS * ROWS];
        List<Island> unplaced = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            ItemStack s = slots.get(i).getItem();
            if (pane(s)) continue;
            String full = StringUtil.stripTrim(s.getHoverName().getString());
            if (i >= 45) {
                Button b = new Button();
                b.slot = i;
                b.label = full.length() > 12 ? full.substring(0, 11) + "." : full;
                btns.add(b);
                continue;
            }
            Island is = new Island();
            is.slot = i;
            is.stack = s;
            int dash = full.indexOf(" - ");
            is.name = dash > 0 ? full.substring(0, dash) : full;
            is.sub = dash > 0 ? full.substring(dash + 3) : "";
            is.locked = s.is(Items.BEDROCK) || locked(s);
            is.color = 0xFF888888;
            is.col = -1;
            String lower = is.name.toLowerCase(Locale.ROOT);
            for (Object[] e : ISLANDS) {
                if (!lower.contains((String) e[0])) continue;
                int cell = (int) e[2] * COLS + (int) e[1];
                is.color = (int) e[3];
                if (!used[cell]) {
                    used[cell] = true;
                    is.col = (int) e[1];
                    is.row = (int) e[2];
                }
                break;
            }
            if (is.col < 0) unplaced.add(is);
            out.add(is);
        }
        int cell = 0;
        for (Island is : unplaced) {
            while (cell < used.length && used[cell]) cell++;
            if (cell >= used.length) {
                out.remove(is);
                continue;
            }
            used[cell] = true;
            is.col = cell % COLS;
            is.row = cell / COLS;
        }
        islands = out;
        buttons = btns;
        containerId = cs.getMenu().containerId;
    }

    private static boolean locked(ItemStack s) {
        for (String l : ItemUtil.loreLines(s)) {
            String t = StringUtil.stripTrim(l).toLowerCase(Locale.ROOT);
            if (t.contains("not unlocked") || t.contains("haven't unlocked") || t.contains("requires")) return true;
        }
        return false;
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return containerId != -1 && cs.getMenu().containerId == containerId && vanillaFor != containerId && FEATURE.active();
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    // ---------------------------------------------------------------- layout

    private static int[] map(int w, int h) {
        int mw = Math.min(w - 40, (h - BAR_H - 50) * 16 / 9);
        int mh = mw * 9 / 16;
        int x = (w - mw) / 2, y = Math.max(20, (h - mh - BAR_H - 10) / 2);
        return new int[]{x, y, mw, mh};
    }

    private static int[] islandRect(int[] m, Island is) {
        int cw = m[2] / COLS, ch = m[3] / ROWS;
        int bw = Math.max(40, cw - 12), bh = Math.max(28, ch - 14);
        int jx = ((is.row * 7 + is.col * 3) % 5 - 2) * cw / 20;
        int jy = ((is.col * 5 + is.row) % 3 - 1) * ch / 16;
        int cx = m[0] + is.col * cw + cw / 2 + jx, cy = m[1] + is.row * ch + ch / 2 + jy;
        return new int[]{cx - bw / 2, cy - bh / 2, bw, bh};
    }

    private static int buttonsX(int w) {
        int n = buttons.size() + 1;
        return (w - (n * BTN_W + (n - 1) * GAP)) / 2;
    }

    // ---------------------------------------------------------------- render

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xF0101014);
        int[] m = map(w, h);
        g.fill(m[0] - 2, m[1] - 2, m[0] + m[2] + 2, m[1] + m[3] + 2, 0xFF2A4A6A);
        g.fill(m[0], m[1], m[0] + m[2], m[1] + m[3], 0xFF1B3550);
        g.centeredText(mc.font, "Fast Travel", w / 2, m[1] - 14, 0xFFFFFFFF);

        Island hovered = null;
        for (Island is : islands) {
            int[] r = islandRect(m, is);
            int x = r[0], y = r[1], x1 = x + r[2], y1 = y + r[3];
            boolean hover = mx >= x && mx < x1 && my >= y && my < y1;
            if (hover) hovered = is;
            int col = is.color;
            g.fill(x + 3, y, x1 - 3, y1, col);
            g.fill(x, y + 3, x1, y1 - 3, col);
            g.fill(x + 3, y1 - 3, x1 - 3, y1, 0x40000000);
            if (hover) {
                g.fill(x + 3, y - 1, x1 - 3, y, 0xFFFFFFFF);
                g.fill(x + 3, y1, x1 - 3, y1 + 1, 0xFFFFFFFF);
                g.fill(x - 1, y + 3, x, y1 - 3, 0xFFFFFFFF);
                g.fill(x1, y + 3, x1 + 1, y1 - 3, 0xFFFFFFFF);
            }
            int cx = (x + x1) / 2;
            g.item(is.stack, cx - 8, y + 3);
            String label = mc.font.width(is.name) > r[2] + 20 ? mc.font.plainSubstrByWidth(is.name, r[2] + 14) : is.name;
            g.centeredText(mc.font, (is.locked ? "§7" : "§f") + label, cx, y + 21, 0xFFFFFFFF);
            if (!is.sub.isEmpty() && r[3] >= 40) g.centeredText(mc.font, "§b" + is.sub, cx, y + 31, 0xFFFFFFFF);
            if (is.locked) {
                g.fill(x, y, x1, y1, 0x90000000);
                g.centeredText(mc.font, "§cLocked", cx, (y + y1) / 2 - 4, 0xFFFFFFFF);
            }
        }
        if (hovered != null) g.setTooltipForNextFrame(mc.font, hovered.stack, mx, my);

        int by = m[1] + m[3] + 10, bx = buttonsX(w);
        for (Button b : buttons) {
            button(g, mc, bx, by, b.label, mx, my);
            bx += BTN_W + GAP;
        }
        button(g, mc, bx, by, "Vanilla", mx, my);
        return true;
    }

    private static void button(GuiGraphicsExtractor g, Minecraft mc, int x, int y, String text, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BAR_H;
        g.fill(x, y, x + BTN_W, y + BAR_H, hover ? 0xFF505060 : 0xFF2C2C36);
        g.centeredText(mc.font, text, x + BTN_W / 2, y + 5, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------- input

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double mx = e.x(), my = e.y();
        if (e.button() > 1) return true;
        int button = e.button();
        int[] m = map(w, h);
        int by = m[1] + m[3] + 10;
        if (my >= by && my < by + BAR_H) {
            int bx = buttonsX(w);
            for (Button b : buttons) {
                if (mx >= bx && mx < bx + BTN_W) {
                    send(cs, b.slot, button);
                    return true;
                }
                bx += BTN_W + GAP;
            }
            if (mx >= bx && mx < bx + BTN_W) vanillaFor = containerId;
            return true;
        }
        for (Island is : islands) {
            int[] r = islandRect(m, is);
            if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) continue;
            send(cs, is.slot, button);
            return true;
        }
        return true;
    }

    private static void send(AbstractContainerScreen<?> cs, int slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return;
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, button, ContainerInput.PICKUP, mc.player);
    }
}
