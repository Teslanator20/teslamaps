package com.teslamaps.features;

// Lean port of Skyblocker's dungeon/partyfinder/PartyFinderScreen.java + PartyEntry.java: the Party Finder as cards
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FancyPartyFinder {

    private static final int CARD_W = 206, CARD_H = 60, GAP = 6, BAR_H = 18, BTN_W = 74, TOP = 40, SEARCH_W = 180;
    private static final Pattern NUMBER_END = Pattern.compile("(\\d+)$");
    private static final Pattern LEVEL = Pattern.compile("\\((\\d+)\\)");

    private static final class Party {
        int slot;
        ItemStack stack;
        String leader = "?", floor = "", note = "", minLevels = "", lockReason = "", search = "";
        final List<String> members = new ArrayList<>();
        boolean locked;
    }

    private static final class Button {
        int slot;
        String label;
    }

    private static List<Party> parties = List.of();
    private static List<Party> shown = List.of();
    private static List<Button> buttons = List.of();
    private static int containerId = -1, vanillaFor = -1, scroll, timer;
    private static long lastClick;
    private static String query = "";
    private static boolean focused;

    private FancyPartyFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("FancyPartyFinder").section("Party").when(() -> cfg().fancyPartyFinder);

    public static void register() {
        FEATURE.tick(FancyPartyFinder::tick)
                .config("Party", b -> {
                    b.toggle("Fancy Party Finder", () -> cfg().fancyPartyFinder, v -> cfg().fancyPartyFinder = v)
                            .desc("Draws the Party Finder as party cards with members, classes, levels, floor, note and lock reason, plus a note search. Search settings and create party stay vanilla.");
                });
    }

    private static boolean pane(ItemStack s) {
        return s.isEmpty() || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("stained_glass_pane");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Party Finder")) {
            if (containerId != -1) {
                containerId = -1;
                focused = false;
            }
            if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
                vanillaFor = -1;
                query = "";
                scroll = 0;
            }
            return;
        }
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() < 54) return;
        if (cs.getMenu().containerId == containerId && timer-- > 0) return;
        timer = 4;
        List<Party> out = new ArrayList<>();
        List<Button> btns = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            ItemStack s = slots.get(i).getItem();
            if (pane(s)) continue;
            String name = StringUtil.stripTrim(s.getHoverName().getString());
            if (i < 45 && s.is(Items.PLAYER_HEAD)) {
                out.add(parse(i, s, name));
                continue;
            }
            if (i < 45 && !s.is(Items.ARROW)) continue;
            Button b = new Button();
            b.slot = i;
            String lower = name.toLowerCase(Locale.ROOT);
            if (s.is(Items.ARROW) && lower.contains("previous")) b.label = "< Prev";
            else if (s.is(Items.ARROW) && lower.contains("next")) b.label = "Next >";
            else b.label = name.length() > 12 ? name.substring(0, 11) + "." : name;
            btns.add(b);
        }
        if (cs.getMenu().containerId != containerId) scroll = 0;
        containerId = cs.getMenu().containerId;
        parties = out;
        buttons = btns;
        filter();
    }

    private static Party parse(int slot, ItemStack s, String name) {
        Party p = new Party();
        p.slot = slot;
        p.stack = s;
        int ap = name.indexOf("'s");
        p.leader = ap > 0 ? name.substring(0, ap) : name;
        ItemLore lore = s.get(DataComponents.LORE);
        String dungeon = "";
        int minClass = -1, minCata = -1;
        if (lore != null) {
            for (Component line : lore.lines()) {
                String t = StringUtil.strip(line.getString());
                String low = t.toLowerCase(Locale.ROOT);
                if (t.startsWith(" ") && t.indexOf(':') > 0) {
                    String who = t.substring(0, t.indexOf(':')).trim();
                    String rest = t.substring(t.indexOf(':') + 1).trim();
                    if (who.equals("Empty") || who.isEmpty()) continue;
                    if (who.equals(p.leader)) continue;
                    String cls = rest.isEmpty() ? "" : rest.split(" ")[0];
                    Matcher lv = LEVEL.matcher(rest);
                    String level = lv.find() ? lv.group(1) : "";
                    p.members.add(classColor(cls) + (cls.isEmpty() ? "?" : cls.substring(0, 1)) + " §e" + level + " §f" + who);
                    continue;
                }
                if (low.contains("class level")) minClass = number(low);
                else if (low.contains("dungeon level")) minCata = number(low);
                else if (low.startsWith("floor:") || low.startsWith("tier:")) p.floor = t.substring(t.indexOf(':') + 1).trim();
                else if (low.startsWith("dungeon:")) dungeon = t.substring(t.indexOf(':') + 1).trim();
                else if (low.startsWith("note:")) p.note = t.substring(t.indexOf(':') + 1).trim();
                else if (!p.locked && red(line)) {
                    p.locked = true;
                    p.lockReason = t.trim();
                }
            }
        }
        if (dungeon.contains("Master")) p.floor = "§cM " + p.floor;
        StringBuilder req = new StringBuilder();
        if (minCata > 0) req.append("§7Cata §f").append(minCata);
        if (minClass > 0) req.append(req.length() > 0 ? "  " : "").append("§7Class §f").append(minClass);
        p.minLevels = req.toString();
        p.search = (p.leader + " " + p.note + " " + p.floor + " " + dungeon).toLowerCase(Locale.ROOT);
        return p;
    }

    private static boolean red(Component line) {
        if (line.getString().startsWith(" ")) return false;
        TextColor c = line.getStyle().getColor();
        if (c == null && !line.getSiblings().isEmpty()) c = line.getSiblings().getFirst().getStyle().getColor();
        if (c == null) return line.getString().startsWith("§c");
        return c.equals(TextColor.fromLegacyFormat(ChatFormatting.RED));
    }

    private static int number(String s) {
        Matcher m = NUMBER_END.matcher(s.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String classColor(String cls) {
        return switch (cls) {
            case "Mage" -> "§b";
            case "Berserk" -> "§c";
            case "Healer" -> "§d";
            case "Archer" -> "§6";
            case "Tank" -> "§a";
            default -> "§7";
        };
    }

    private static void filter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            shown = parties;
            return;
        }
        List<Party> out = new ArrayList<>();
        for (Party p : parties) if (p.search.contains(q)) out.add(p);
        shown = out;
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return containerId != -1 && cs.getMenu().containerId == containerId && vanillaFor != containerId && FEATURE.active();
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    // ---------------------------------------------------------------- layout

    private static int cols(int w) {
        return Math.max(1, Math.min(3, (w - 20 + GAP) / (CARD_W + GAP)));
    }

    private static int gridX(int w) {
        int c = cols(w);
        return (w - (c * CARD_W + (c - 1) * GAP)) / 2;
    }

    private static int bottom(int h) {
        return h - BAR_H - 10;
    }

    private static int visibleRows(int h) {
        return Math.max(1, (bottom(h) - 6 - TOP + GAP) / (CARD_H + GAP));
    }

    private static int maxScroll(int w, int h) {
        int rows = (shown.size() + cols(w) - 1) / cols(w);
        return Math.max(0, rows - visibleRows(h));
    }

    private static int buttonsX(int w) {
        int n = buttons.size() + 1;
        return (w - (n * BTN_W + (n - 1) * GAP)) / 2;
    }

    // ---------------------------------------------------------------- render

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xF0101014);
        g.centeredText(font, "Party Finder", w / 2, 8, 0xFFFFFFFF);

        int sx = (w - SEARCH_W) / 2, sy = 20;
        g.fill(sx - 1, sy - 1, sx + SEARCH_W + 1, sy + 13, focused ? 0xFFAAAAAA : 0xFF555555);
        g.fill(sx, sy, sx + SEARCH_W, sy + 12, 0xFF000000);
        String text = query.isEmpty() && !focused ? "§8Search note, leader, floor..." : query + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        g.text(font, font.plainSubstrByWidth(text, SEARCH_W - 6), sx + 3, sy + 2, 0xFFFFFFFF, false);

        List<Party> list = shown;
        int c = cols(w), gx = gridX(w), rows = visibleRows(h);
        scroll = Math.min(scroll, maxScroll(w, h));
        Party hovered = null;
        if (list.isEmpty()) {
            g.centeredText(font, parties.isEmpty() ? "§7No parties found" : "§7No party matches the search", w / 2, TOP + 20, 0xFFFFFFFF);
        }
        for (int i = scroll * c; i < list.size() && i < (scroll + rows) * c; i++) {
            Party p = list.get(i);
            int idx = i - scroll * c;
            int x = gx + (idx % c) * (CARD_W + GAP), y = TOP + (idx / c) * (CARD_H + GAP);
            boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
            if (hover) hovered = p;
            g.fill(x, y, x + CARD_W, y + CARD_H, hover && !p.locked ? 0xFF3A3A48 : 0xFF24242C);
            g.fill(x, y, x + 2, y + CARD_H, p.locked ? 0xFF803030 : 0xFF3F8FD0);
            g.item(p.stack, x + 5, y + 4);
            g.text(font, "§f§l" + p.leader, x + 24, y + 4, 0xFFFFFFFF, true);
            if (!p.floor.isEmpty()) g.text(font, "§e" + p.floor, x + CARD_W - 5 - font.width("§e" + p.floor), y + 4, 0xFFFFFFFF, true);
            if (!p.minLevels.isEmpty()) g.text(font, p.minLevels, x + 24, y + 14, 0xFFFFFFFF, false);
            for (int m = 0; m < p.members.size() && m < 4; m++) {
                g.text(font, p.members.get(m), x + 6 + (m % 2) * (CARD_W / 2), y + 26 + (m / 2) * 10, 0xFFFFFFFF, true);
            }
            if (!p.note.isEmpty()) g.text(font, "§7" + font.plainSubstrByWidth(p.note, CARD_W - 12), x + 6, y + 48, 0xFFFFFFFF, false);
            if (p.locked) {
                g.fill(x, y, x + CARD_W, y + CARD_H, 0x90000000);
                String r = font.plainSubstrByWidth(p.lockReason, CARD_W - 10);
                int rw = font.width(r);
                g.fill(x + (CARD_W - rw) / 2 - 3, y + CARD_H / 2 - 5, x + (CARD_W + rw) / 2 + 3, y + CARD_H / 2 + 6, 0x7F000000);
                g.centeredText(font, "§c" + r, x + CARD_W / 2, y + CARD_H / 2 - 4, 0xFFFFFFFF);
            }
        }
        int max = maxScroll(w, h);
        if (max > 0) g.centeredText(font, "§8scroll " + (scroll + 1) + "/" + (max + 1), w / 2, bottom(h) - 10, 0xFFFFFFFF);

        int by = bottom(h), bx = buttonsX(w);
        Button hoveredButton = null;
        for (Button b : buttons) {
            if (button(g, font, bx, by, b.label, mx, my)) hoveredButton = b;
            bx += BTN_W + GAP;
        }
        button(g, font, bx, by, "Vanilla", mx, my);
        if (hovered != null) g.setTooltipForNextFrame(font, hovered.stack, mx, my);
        else if (hoveredButton != null) {
            ItemStack st = cs.getMenu().slots.get(hoveredButton.slot).getItem();
            if (!st.isEmpty()) g.setTooltipForNextFrame(font, st, mx, my);
        }
        return true;
    }

    private static boolean button(GuiGraphicsExtractor g, Font font, int x, int y, String text, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BAR_H;
        g.fill(x, y, x + BTN_W, y + BAR_H, hover ? 0xFF505060 : 0xFF2C2C36);
        g.centeredText(font, text, x + BTN_W / 2, y + 5, 0xFFFFFFFF);
        return hover;
    }

    // ---------------------------------------------------------------- input

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double mx = e.x(), my = e.y();
        int button = e.button();
        int sx = (w - SEARCH_W) / 2, sy = 20;
        boolean inSearch = mx >= sx && mx < sx + SEARCH_W && my >= sy && my < sy + 12;
        if (inSearch) {
            if (button == 1) {
                query = "";
                filter();
            }
            focused = true;
            return true;
        }
        focused = false;
        if (button > 1) return true;
        int by = bottom(h);
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
        List<Party> list = shown;
        int c = cols(w), gx = gridX(w), rows = visibleRows(h);
        for (int i = scroll * c; i < list.size() && i < (scroll + rows) * c; i++) {
            int idx = i - scroll * c;
            int x = gx + (idx % c) * (CARD_W + GAP), y = TOP + (idx / c) * (CARD_H + GAP);
            if (mx < x || mx >= x + CARD_W || my < y || my >= y + CARD_H) continue;
            Party p = list.get(i);
            if (button == 1) {
                PartyFinderHelper.rightClickAction(p.stack);
                return true;
            }
            send(cs, p.slot, 0);
            return true;
        }
        return true;
    }

    public static boolean scrolled(double amount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !active(cs)) return false;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        scroll = Math.max(0, Math.min(maxScroll(w, h), scroll + (amount < 0 ? 1 : -1)));
        return true;
    }

    public static boolean keyPressed(AbstractContainerScreen<?> cs, int key) {
        if (!active(cs) || !focused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
                filter();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            focused = false;
            return true;
        }
        return true;
    }

    public static boolean charTyped(int codepoint) {
        if (!focused || codepoint < 32 || codepoint == 127) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !active(cs)) return false;
        if (query.length() < 40) query += Character.toString(codepoint);
        scroll = 0;
        filter();
        return true;
    }

    private static void send(AbstractContainerScreen<?> cs, int slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return;
        lastClick = now;
        timer = 0;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, button, ContainerInput.PICKUP, mc.player);
    }
}
