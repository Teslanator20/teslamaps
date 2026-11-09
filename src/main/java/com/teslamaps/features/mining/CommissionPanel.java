package com.teslamaps.features.mining;

// Lean port of Bax's features/screen/commission/CommissionScreen.java: drawn over the Hypixel chest
// like FancyAuctionHouse, cards and buttons click the real slots
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Commissions menu as one card per commission with a progress bar; Milestones / Filter / Close as buttons. */
public final class CommissionPanel {

    private record Card(String name, float progress, String progressText, boolean done, int slot, List<Component> lore) {}

    private static final Pattern FRACTION = Pattern.compile("([\\d,]+)/([\\d,]+)");
    private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
    private static final int PW = 280, PAD = 10, HEADER = 28, CARD_H = 38, GAP = 3, BAR = 4, NAV_H = 18, FOOTER = 30;
    private static final String[] NAV_DEFAULT = {"Close", "Milestones", "Filter"};

    private static Feature feature;
    private static AbstractContainerScreen<?> screen;
    private static boolean vanilla;
    private static int ticks, layoutCount;
    private static long lastClick;
    private static List<Card> cards = List.of();
    private static final int[] navSlots = {-1, -1, -1};
    private static final String[] navLabels = NAV_DEFAULT.clone();
    private static final List<List<Component>> navLore = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    private CommissionPanel() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CommissionPanel").section("Mining").when(() -> cfg().commissionScreen)
                .tick(CommissionPanel::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Commissions Screen");
                    b.toggle("Custom Commissions Screen", () -> c.commissionScreen, v -> c.commissionScreen = v)
                            .desc("Replaces the Commissions chest with one card per commission: name, progress bar, green when done. Click a card to claim it. A Vanilla button shows Hypixel's menu.")
                            .tags("commission", "commissions", "screen", "menu", "progress");
                    b.toggle("  Hide Tooltip Bloat", () -> c.commissionScreenHideBloat, v -> c.commissionScreenHideBloat = v)
                            .desc("Card tooltips skip the intro text and start at the commission itself");
                });
    }

    private static boolean isCommissions(AbstractContainerScreen<?> cs) {
        return StringUtil.stripTrim(cs.getTitle().getString()).equals("Commissions");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !isCommissions(cs)) {
            if (screen != null) {
                screen = null;
                vanilla = false;
                cards = List.of();
                layoutCount = 0;
            }
            return;
        }
        if (cs != screen) {
            screen = cs;
            layoutCount = 0;
            cards = List.of();
            ticks = 0;
        }
        if (ticks++ % 5 == 0) build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        List<Card> parsed = new ArrayList<>();
        int[] slots = {-1, -1, -1};
        String[] labels = NAV_DEFAULT.clone();
        List<List<Component>> lore = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            String low = name.toLowerCase(Locale.ROOT);
            int nav = st.is(Items.BARRIER) || low.equals("close") ? 0 : low.contains("milestone") ? 1
                    : low.contains("filter") || low.contains("type") ? 2 : -1;
            if (nav >= 0) {
                slots[nav] = slot.index;
                labels[nav] = name.isEmpty() ? NAV_DEFAULT[nav] : name;
                collectLore(st, lore.get(nav));
                continue;
            }
            if (filler(st) || name.isEmpty()) continue;
            parsed.add(parse(st, name, slot.index));
        }
        System.arraycopy(slots, 0, navSlots, 0, 3);
        System.arraycopy(labels, 0, navLabels, 0, 3);
        for (int i = 0; i < 3; i++) { navLore.get(i).clear(); navLore.get(i).addAll(lore.get(i)); }
        // a claimed commission vanishes before its replacement arrives; keep the old cards until then
        if (parsed.size() < layoutCount) return;
        cards = parsed;
        layoutCount = Math.max(layoutCount, parsed.size());
    }

    private static boolean filler(ItemStack st) {
        return st.is(Items.STAINED_GLASS_PANE.black()) || st.is(Items.STAINED_GLASS_PANE.gray())
                || st.is(Items.STAINED_GLASS_PANE.red()) || st.is(Items.STAINED_GLASS_PANE.white())
                || st.is(Items.STAINED_GLASS_PANE.lightGray()) || st.is(Items.ARROW);
    }

    private static void collectLore(ItemStack st, List<Component> out) {
        out.clear();
        out.add(st.getHoverName());
        ItemLore l = st.get(DataComponents.LORE);
        if (l != null) out.addAll(l.lines());
    }

    private static Card parse(ItemStack st, String name, int slot) {
        List<Component> lore = new ArrayList<>();
        collectLore(st, lore);
        String type = "";
        boolean blank = false, done = false;
        float progress = 0;
        String text = "";
        for (int i = 1; i < lore.size(); i++) {
            String l = StringUtil.stripTrim(lore.get(i).getString());
            if (!blank && l.isEmpty()) blank = true;
            else if (blank && type.isEmpty() && !l.isEmpty()) type = l;
            if (l.contains("COMPLETED")) {
                done = true;
                progress = 1f;
                text = "DONE";
            }
            if (done) continue;
            Matcher f = FRACTION.matcher(l);
            if (f.find()) {
                try {
                    float cur = Float.parseFloat(f.group(1).replace(",", "")), total = Float.parseFloat(f.group(2).replace(",", ""));
                    if (total > 0) {
                        progress = Math.min(cur / total, 1f);
                        text = f.group(1) + "/" + f.group(2);
                    }
                } catch (NumberFormatException ignored) {}
                continue;
            }
            Matcher p = PERCENT.matcher(l);
            if (p.find()) {
                try {
                    progress = Math.min(Float.parseFloat(p.group(1)) / 100f, 1f);
                    text = p.group(1) + "%";
                } catch (NumberFormatException ignored) {}
            }
        }
        if (text.isEmpty()) text = Math.round(progress * 100) + "%";
        return new Card(type.isEmpty() ? name : type, progress, text, done, slot, lore);
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return !vanilla && cs == screen && feature != null && feature.active();
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    private static int panelHeight() {
        int n = Math.max(layoutCount, 1);
        return HEADER + 1 + PAD + n * (CARD_H + GAP) - GAP + PAD + FOOTER;
    }

    private static float scale(int h) {
        return Math.min(1f, (h - 16) / (float) panelHeight());
    }

    private static boolean in(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Font font = Minecraft.getInstance().font;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xC0101014);
        float s = scale(h);
        int ph = panelHeight();
        int ox = Math.round((w - PW * s) / 2f), oy = Math.round((h - ph * s) / 2f);
        double lx = (mx - ox) / s, ly = (my - oy) / s;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(s, s);
        TeslaGui.hudPanel(g, 0, 0, PW, ph, 5);
        g.centeredText(font, "§lCommissions", PW / 2, (HEADER - 8) / 2, 0xFFCCCCDD);
        g.fill(1, HEADER, PW - 1, HEADER + 1, 0xFF2A2A35);
        boolean vHover = in(lx, ly, PW - 50, 7, 44, 13);
        g.fill(PW - 50, 7, PW - 6, 20, vHover ? 0xFF505060 : 0xFF2C2C36);
        g.centeredText(font, "§7Vanilla", PW - 28, 10, 0xFFFFFFFF);

        List<Component> tip = null;
        int cy = HEADER + 1 + PAD, cw = PW - PAD * 2;
        if (cards.isEmpty()) g.text(font, "§7No commissions found", PAD, cy + 10, 0xFFFFFFFF, false);
        for (Card c : cards) {
            boolean hover = in(lx, ly, PAD, cy, cw, CARD_H);
            int bg = c.done ? (hover ? 0xFF223020 : 0xFF1A2518) : (hover ? 0xFF262633 : 0xFF1E1E28);
            g.fill(PAD, cy, PAD + cw, cy + CARD_H, bg);
            border(g, PAD, cy, cw, CARD_H, c.done ? 0xFF2D4A25 : 0xFF2A2A35);
            g.text(font, c.name, PAD + 8, cy + 6, c.done ? 0xFF5CB85C : 0xFFE0E0E0, false);
            String pt = c.done ? "COMPLETED" : c.progressText;
            g.text(font, pt, PAD + cw - 8 - font.width(pt), cy + 6, c.done ? 0xFF5CB85C : 0xFF8A8A96, false);
            int bx = PAD + 8, by = cy + CARD_H - BAR - 8, bw = cw - 16;
            g.fill(bx, by, bx + bw, by + BAR, 0xFF0D0D14);
            int fw = (int) (bw * c.progress);
            if (fw > 0) g.fill(bx, by, bx + fw, by + BAR, c.done ? 0xFF3D8C40 : 0xFF3B7DD8);
            if (hover) tip = cfg().commissionScreenHideBloat ? trimmed(c.lore) : c.lore;
            cy += CARD_H + GAP;
        }

        int footer = ph - FOOTER;
        g.fill(1, footer, PW - 1, footer + 1, 0xFF2A2A35);
        int[] widths = navWidths(font);
        int nx = (PW - widths[3]) / 2, ny = footer + (FOOTER - NAV_H) / 2;
        for (int i = 0; i < 3; i++) {
            if (navSlots[i] < 0) continue;
            boolean hover = in(lx, ly, nx, ny, widths[i], NAV_H);
            g.fill(nx, ny, nx + widths[i], ny + NAV_H, hover ? 0xFF2A2A38 : 0xFF1E1E28);
            border(g, nx, ny, widths[i], NAV_H, 0xFF2A2A35);
            g.centeredText(font, navLabels[i], nx + widths[i] / 2, ny + (NAV_H - 8) / 2, hover ? 0xFFE0E0E0 : 0xFF9A9AAA);
            if (hover && !navLore.get(i).isEmpty()) tip = navLore.get(i);
            nx += widths[i] + 4;
        }
        g.pose().popMatrix();
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mx, my);
        return true;
    }

    /** Button widths, index 3 = total incl. gaps. */
    private static int[] navWidths(Font font) {
        int[] out = new int[4];
        int count = 0;
        for (int i = 0; i < 3; i++) {
            if (navSlots[i] < 0) continue;
            out[i] = font.width(navLabels[i]) + 16;
            out[3] += out[i];
            count++;
        }
        if (count > 1) out[3] += (count - 1) * 4;
        return out;
    }

    private static List<Component> trimmed(List<Component> lore) {
        for (int i = 1; i < lore.size(); i++) {
            if (StringUtil.stripTrim(lore.get(i).getString()).isEmpty()) {
                return i + 1 < lore.size() ? lore.subList(i + 1, lore.size()) : lore;
            }
        }
        return lore;
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        if (e.button() > 1) return true;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float s = scale(h);
        int ph = panelHeight();
        int ox = Math.round((w - PW * s) / 2f), oy = Math.round((h - ph * s) / 2f);
        double lx = (e.x() - ox) / s, ly = (e.y() - oy) / s;
        if (in(lx, ly, PW - 50, 7, 44, 13)) { vanilla = true; return true; }
        int cy = HEADER + 1 + PAD, cw = PW - PAD * 2;
        for (Card c : cards) {
            if (in(lx, ly, PAD, cy, cw, CARD_H)) { send(cs, c.slot, 0); return true; }
            cy += CARD_H + GAP;
        }
        int[] widths = navWidths(mc.font);
        int nx = (PW - widths[3]) / 2, ny = ph - FOOTER + (FOOTER - NAV_H) / 2;
        for (int i = 0; i < 3; i++) {
            if (navSlots[i] < 0) continue;
            if (in(lx, ly, nx, ny, widths[i], NAV_H)) {
                if (i == 0) cs.onClose();
                else send(cs, navSlots[i], e.button());
                return true;
            }
            nx += widths[i] + 4;
        }
        return true;
    }

    private static void send(AbstractContainerScreen<?> cs, int slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || slot < 0 || slot >= cs.getMenu().slots.size()) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 150) return;
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, button, ContainerInput.PICKUP, mc.player);
    }
}
