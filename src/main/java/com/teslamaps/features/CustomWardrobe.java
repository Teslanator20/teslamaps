package com.teslamaps.features;

// Ported (slimmed) from SkyHanni's CustomWardrobe.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomWardrobe {

    private static final Pattern PAGE = Pattern.compile("\\((\\d+)/(\\d+)\\)");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int GAP = 6, BAR_H = 20, BTN_W = 60;

    private static final class Card {
        int col, id;
        final ItemStack[] armor = new ItemStack[4];
        boolean equipped, locked, empty;
        double value;
        String valueText = "";
        RemotePlayer player;
    }

    private static final Card[] cards = new Card[9];
    private static List<Card> visible = List.of();
    private static int containerId = -1, vanillaFor = -1;
    private static int page = 1, pages = 1;
    private static boolean hasPrev, hasNext;
    private static long lastClick;
    private static net.minecraft.client.multiplayer.ClientLevel playerLevel;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("CustomWardrobe").section("Inventory").when(() -> cfg().customWardrobe);

    public static void register() {
        FEATURE.tick(CustomWardrobe::tick);
    }

    private static boolean isArmorWardrobe(String title) {
        return title.endsWith("Armor Sets") || title.startsWith("Wardrobe");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !isArmorWardrobe(StringUtil.stripTrim(cs.getTitle().getString()))) {
            containerId = -1;
            return;
        }
        if (playerLevel != mc.level) {
            for (int i = 0; i < 9; i++) cards[i] = null;
            playerLevel = mc.level;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        Matcher m = PAGE.matcher(title);
        page = 1;
        pages = 1;
        if (m.find()) {
            page = Integer.parseInt(m.group(1));
            pages = Integer.parseInt(m.group(2));
        }
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() < 54) return;
        containerId = cs.getMenu().containerId;
        hasPrev = named(slots.get(45).getItem(), "Previous Page");
        hasNext = named(slots.get(53).getItem(), "Next Page");

        List<Card> out = new ArrayList<>();
        java.util.Set<Integer> favs = cfg().wardrobeFavorites;
        for (int col = 0; col < 9; col++) {
            Card c = cards[col];
            if (c == null) {
                c = cards[col] = new Card();
                c.col = col;
                c.player = newPlayer(mc);
            }
            c.id = (page - 1) * 9 + col + 1;
            boolean changed = false, any = false;
            for (int row = 0; row < 4; row++) {
                ItemStack s = slots.get(row * 9 + col).getItem();
                if (s.isEmpty() || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("stained_glass_pane")) s = ItemStack.EMPTY;
                if (!s.isEmpty()) any = true;
                if (c.armor[row] != s) {
                    changed = true;
                    c.armor[row] = s;
                    if (c.player != null) c.player.setItemSlot(ARMOR[row], s.copy());
                }
            }
            ItemStack button = slots.get(36 + col).getItem();
            String bn = StringUtil.stripTrim(button.getHoverName().getString());
            c.equipped = bn.endsWith("Equipped") || button.is(Items.LIME_DYE);
            c.locked = button.is(Items.RED_DYE) || bn.contains("Locked") || button.isEmpty();
            c.empty = !any;
            if (changed) {
                double v = 0;
                for (ItemStack s : c.armor) if (s != null && !s.isEmpty()) v += EstimatedValue.compute(s);
                c.value = v;
                c.valueText = v > 0 ? "§6" + EstimatedValue.fmt(v) : "";
            }
            if (c.locked && c.empty) continue;
            if (cfg().customWardrobeHideEmpty && c.empty) continue;
            if (cfg().customWardrobeOnlyFavorites && !favs.contains(c.id)) continue;
            out.add(c);
        }
        visible = out;
    }

    private static boolean named(ItemStack s, String name) {
        return !s.isEmpty() && StringUtil.stripTrim(s.getHoverName().getString()).startsWith(name);
    }

    private static RemotePlayer newPlayer(Minecraft mc) {
        if (mc.level == null) return null;
        return new RemotePlayer(mc.level, mc.getGameProfile()) {
            @Override public boolean isInvisibleTo(Player player) { return true; }
            @Override public void onEquipItem(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {}
            @Override public boolean isModelPartShown(PlayerModelPart part) {
                return part != PlayerModelPart.CAPE && mc.options.isModelPartEnabled(part);
            }
            @Override public int getId() { return -1; }
        };
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return containerId != -1 && cs.getMenu().containerId == containerId && vanillaFor != containerId
                && FEATURE.active();
    }

    // ---------------------------------------------------------------- layout

    private record Layout(int cols, int x0, int y0, int cw, int ch, int barY) {}

    private static Layout layout(int w, int h) {
        int cw = Math.max(40, cfg().customWardrobeCardWidth), ch = Math.max(60, cfg().customWardrobeCardHeight);
        int n = Math.max(1, visible.size());
        int cols = Math.max(1, Math.min(n, (w - 20 + GAP) / (cw + GAP)));
        int rows = (n + cols - 1) / cols;
        int gw = cols * (cw + GAP) - GAP, gh = rows * (ch + GAP) - GAP;
        int x0 = (w - gw) / 2, y0 = Math.max(20, (h - gh - BAR_H - 10) / 2);
        return new Layout(cols, x0, y0, cw, ch, y0 + gh + 10);
    }

    private static int[] cardPos(Layout l, int i) {
        return new int[]{l.x0 + (i % l.cols) * (l.cw + GAP), l.y0 + (i / l.cols) * (l.ch + GAP)};
    }

    private static int[] buttonX(int w) {
        int total = BTN_W * 3 + GAP * 2, x = (w - total) / 2;
        return new int[]{x, x + BTN_W + GAP, x + 2 * (BTN_W + GAP)};
    }

    // ---------------------------------------------------------------- render

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xF0101014);
        Layout l = layout(w, h);
        int eqCol = TeslaMapsConfig.parseColor(c.colorWardrobeEquipped);
        int favCol = TeslaMapsConfig.parseColor(c.colorWardrobeFavorite);
        g.centeredText(mc.font, "Wardrobe  " + page + "/" + pages, w / 2, l.y0 - 14, 0xFFFFFFFF);
        if (visible.isEmpty()) g.centeredText(mc.font, "§7No sets to show", w / 2, l.y0 + 20, 0xFFFFFFFF);

        for (int i = 0; i < visible.size(); i++) {
            Card card = visible.get(i);
            int[] p = cardPos(l, i);
            int x = p[0], y = p[1], x1 = x + l.cw, y1 = y + l.ch;
            boolean hover = mx >= x && mx < x1 && my >= y && my < y1;
            boolean fav = c.wardrobeFavorites.contains(card.id);
            int border = card.equipped ? eqCol : fav ? favCol : hover ? 0xFFFFFFFF : 0xFF3A3A44;
            g.fill(x, y, x1, y1, border);
            g.fill(x + 1, y + 1, x1 - 1, y1 - 1, hover ? 0xFF2A2A34 : 0xFF1C1C24);
            g.text(mc.font, (fav ? "§6★ " : "") + (card.locked ? "§c" : "§f") + "Slot " + card.id, x + 3, y + 3, 0xFFFFFFFF, true);

            int modelBottom = y1 - 20 - (c.customWardrobeValue && !card.valueText.isEmpty() ? 10 : 0);
            if (card.player != null && !card.empty) {
                int size = Math.max(16, Math.min(l.cw / 2, (modelBottom - y - 14) * 2 / 5));
                float fx = c.customWardrobeFollowMouse ? mx : (x + x1) / 2f;
                float fy = c.customWardrobeFollowMouse ? my : (y + 14 + modelBottom) / 2f;
                InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 14, x1 - 2, modelBottom,
                        size, 0.0625f, fx, fy, card.player);
            } else if (card.empty) {
                g.centeredText(mc.font, card.locked ? "§cLocked" : "§8Empty", (x + x1) / 2, (y + modelBottom) / 2, 0xFFFFFFFF);
            }
            if (c.customWardrobeValue && !card.valueText.isEmpty())
                g.centeredText(mc.font, card.valueText, (x + x1) / 2, y1 - 29, 0xFFFFFFFF);

            int ix = x + (l.cw - 64) / 2, iy = y1 - 18;
            for (int j = 0; j < 4; j++) {
                ItemStack s = card.armor[j];
                if (s == null || s.isEmpty()) continue;
                int sx = ix + j * 16;
                g.item(s, sx, iy);
                if (mx >= sx && mx < sx + 16 && my >= iy && my < iy + 16) g.setTooltipForNextFrame(mc.font, s, mx, my);
            }
            if (hover && my < iy && !card.empty) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§eSlot " + card.id + (card.equipped ? " §a(Equipped)" : "")));
                for (ItemStack s : card.armor) if (s != null && !s.isEmpty()) tip.add(s.getHoverName());
                if (card.value > 0) tip.add(Component.literal("§7Value: §6" + EstimatedValue.fmt(card.value)));
                tip.add(Component.literal(card.equipped ? "§7Click to unequip" : "§7Click to equip"));
                tip.add(Component.literal("§7Right-click: " + (fav ? "remove favorite" : "favorite")));
                g.setComponentTooltipForNextFrame(mc.font, tip, mx, my);
            }
        }

        int[] bx = buttonX(w);
        button(g, mc, bx[0], l.barY, "< Prev", hasPrev, mx, my);
        button(g, mc, bx[1], l.barY, "Vanilla", true, mx, my);
        button(g, mc, bx[2], l.barY, "Next >", hasNext, mx, my);
        return true;
    }

    private static void button(GuiGraphicsExtractor g, Minecraft mc, int x, int y, String text, boolean on, int mx, int my) {
        boolean hover = on && mx >= x && mx < x + BTN_W && my >= y && my < y + BAR_H;
        g.fill(x, y, x + BTN_W, y + BAR_H, hover ? 0xFF505060 : 0xFF2C2C36);
        g.centeredText(mc.font, (on ? "§f" : "§8") + text, x + BTN_W / 2, y + 6, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------- input

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double mx = e.x(), my = e.y();
        Layout l = layout(w, h);
        int[] bx = buttonX(w);
        if (my >= l.barY && my < l.barY + BAR_H) {
            if (mx >= bx[0] && mx < bx[0] + BTN_W && hasPrev) send(cs, 45);
            else if (mx >= bx[1] && mx < bx[1] + BTN_W) vanillaFor = containerId;
            else if (mx >= bx[2] && mx < bx[2] + BTN_W && hasNext) send(cs, 53);
            return true;
        }
        for (int i = 0; i < visible.size(); i++) {
            int[] p = cardPos(l, i);
            if (mx < p[0] || mx >= p[0] + l.cw || my < p[1] || my >= p[1] + l.ch) continue;
            Card card = visible.get(i);
            if (e.button() == 1) {
                java.util.Set<Integer> favs = cfg().wardrobeFavorites;
                if (!favs.remove(card.id)) favs.add(card.id);
                TeslaMapsConfig.save();
            } else if (e.button() == 0 && !card.locked && !card.empty) {
                send(cs, 36 + card.col);
            }
            return true;
        }
        return true;
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    private static void send(AbstractContainerScreen<?> cs, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return;
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
