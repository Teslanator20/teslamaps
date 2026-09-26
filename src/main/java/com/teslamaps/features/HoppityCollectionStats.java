package com.teslamaps.features;

// Ported from SkyHanni's HoppityCollectionStats.kt (patterns + panel), rarity from the item instead of the NEU repo
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HoppityCollectionStats {

    private static final int LINE = 10;
    private static final Pattern PAGE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Hoppity's Collection");
    private static final Pattern DUPES = Pattern.compile("Duplicates Found: ([\\d,]+)");
    private static final Pattern FOUND_BAR = Pattern.compile("^\\s{4,}(\\d+)/(\\d+)$");
    private static final Pattern REQ_AMOUNT = Pattern.compile("✖ Requirement ([\\d,]+)/([\\d,]+)");
    private static final Pattern LOCATION = Pattern.compile("Find 15 unique egg locations in (?:the )?(.*?)\\..*");
    private static final Pattern RARITY_LINE = Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)(?: RABBIT)?$");
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
    private static final String[] RARITY_COLORS = {"§f", "§a", "§9", "§5", "§6", "§d", "§b"};

    private static List<String> lines = List.of();
    private static List<Integer> highlight = List.of();
    private static Screen lastScreen;
    private static boolean dirty;
    private static int ticks;
    private static int hypixelFound = -1, hypixelTotal = -1;

    private HoppityCollectionStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HoppityCollectionStats").section("Events")
                .when(() -> cfg().hoppityCollectionStats)
                .tick(HoppityCollectionStats::tick)
                .config("Events", b -> {
                    b.label("Hoppity");
                    b.toggle("Hoppity Collection Stats", () -> cfg().hoppityCollectionStats, v -> cfg().hoppityCollectionStats = v)
                            .desc("Panel in Hoppity's Collection: found rabbits per rarity, duplicates, missing egg locations.");
                    b.toggle("  Highlight Obtainable Rabbits", () -> cfg().hoppityHighlightRequirements,
                            v -> cfg().hoppityHighlightRequirements = v)
                            .desc("Green = not found yet, requirement already met.");
                });
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = cfg();
        return c.hoppityCollectionStats && c.section("Events") && !lines.isEmpty()
                && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean inMenu = mc.screen instanceof AbstractContainerScreen<?> cs
                && PAGE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches();
        if (!inMenu) {
            if (lastScreen != null) {
                lastScreen = null;
                lines = List.of();
                highlight = List.of();
                if (dirty) {
                    dirty = false;
                    TeslaMapsConfig.save();
                }
            }
            return;
        }
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            ticks = 0;
        }
        if (ticks++ % 10 != 0) return;
        scan((AbstractContainerScreen<?>) mc.screen);
    }

    private static String rarity(ItemStack st, List<String> lore) {
        for (String l : lore) {
            Matcher m = RARITY_LINE.matcher(l.trim());
            if (m.matches()) return m.group(1);
        }
        TextColor[] col = {null};
        Component name = st.getHoverName();
        name.visit((FormattedText.StyledContentConsumer<Object>) (style, part) -> {
            if (col[0] == null && !part.isBlank() && style.getColor() != null) col[0] = style.getColor();
            return Optional.empty();
        }, Style.EMPTY);
        String raw = name.getString();
        ChatFormatting f = null;
        if (raw.length() > 1 && raw.charAt(0) == '§') f = ChatFormatting.getByCode(raw.charAt(1));
        if (f == null && col[0] != null) {
            for (ChatFormatting cf : ChatFormatting.values()) {
                if (cf.isColor() && TextColor.fromLegacyFormat(cf).equals(col[0])) { f = cf; break; }
            }
        }
        if (f == null) return null;
        return switch (f) {
            case WHITE -> "COMMON";
            case GREEN -> "UNCOMMON";
            case BLUE -> "RARE";
            case DARK_PURPLE -> "EPIC";
            case GOLD -> "LEGENDARY";
            case LIGHT_PURPLE -> "MYTHIC";
            case AQUA -> "DIVINE";
            default -> null;
        };
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static void scan(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = cfg();
        List<Integer> hl = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            if (lore.isEmpty()) continue;
            boolean notFound = false, reqMet = false, isRabbit = false;
            int dupes = 0;
            for (String l : lore) {
                Matcher bar = FOUND_BAR.matcher(l);
                if (bar.matches()) {
                    hypixelFound = num(bar.group(1));
                    hypixelTotal = num(bar.group(2));
                }
                if (l.contains("You have not found this rabbit yet!") || l.contains("You cannot find this rabbit until you")) {
                    notFound = true;
                    isRabbit = true;
                }
                Matcher d = DUPES.matcher(l);
                if (d.find()) {
                    dupes = num(d.group(1));
                    isRabbit = true;
                }
                if (l.contains("✔ Requirement")) reqMet = true;
            }
            if (!isRabbit) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (name.isEmpty()) continue;
            String r = rarity(st, lore);
            if (r != null && !r.equals(c.hoppityRabbitRarity.get(name))) {
                c.hoppityRabbitRarity.put(name, r);
                dirty = true;
            }
            if (notFound) {
                if (c.hoppityRabbits.remove(name) != null) dirty = true;
                if (reqMet) hl.add(slot.index);
            } else {
                Integer old = c.hoppityRabbits.put(name, dupes + 1);
                if (old == null || old != dupes + 1) dirty = true;
            }
            saveLocation(c, name, lore);
        }
        highlight = hl;
        build(c);
    }

    private static void saveLocation(TeslaMapsConfig c, String name, List<String> lore) {
        int have = -1, need = -1, start = -1;
        for (int i = 0; i < lore.size(); i++) {
            String l = lore.get(i);
            if (l.contains("✔ Requirement")) { have = 15; need = 15; start = i + 1; break; }
            Matcher m = REQ_AMOUNT.matcher(l);
            if (m.find()) { have = num(m.group(1)); need = num(m.group(2)); start = i + 1; break; }
        }
        if (start < 0) return;
        StringBuilder desc = new StringBuilder();
        for (int i = start; i < lore.size() && !lore.get(i).isBlank(); i++) {
            if (!desc.isEmpty()) desc.append(' ');
            desc.append(lore.get(i).trim());
        }
        Matcher loc = LOCATION.matcher(desc);
        if (!loc.matches()) return;
        String v = loc.group(1) + "|" + have + "|" + need;
        if (!v.equals(c.hoppityLocationRabbits.put(name, v))) dirty = true;
    }

    private static void build(TeslaMapsConfig c) {
        int[] found = new int[RARITIES.length], seen = new int[RARITIES.length], dup = new int[RARITIES.length];
        for (Map.Entry<String, String> e : c.hoppityRabbitRarity.entrySet()) {
            int idx = indexOf(e.getValue());
            if (idx < 0) continue;
            seen[idx]++;
            Integer n = c.hoppityRabbits.get(e.getKey());
            if (n != null) {
                found[idx]++;
                dup[idx] += Math.max(0, n - 1);
            }
        }
        List<String> out = new ArrayList<>();
        out.add("§e§lHoppity Collection");
        int totalFound = 0, totalDup = 0;
        for (int i = 0; i < RARITIES.length; i++) {
            totalFound += found[i];
            totalDup += dup[i];
            if (seen[i] == 0) continue;
            String name = RARITIES[i].charAt(0) + RARITIES[i].substring(1).toLowerCase(java.util.Locale.ROOT);
            out.add(RARITY_COLORS[i] + name + " §a" + found[i] + "§7/§a" + seen[i]
                    + (dup[i] > 0 ? " §8(+" + dup[i] + " dupes)" : ""));
        }
        int loggedFound = c.hoppityRabbits.size();
        int total = hypixelTotal > 0 ? hypixelTotal : c.hoppityRabbitRarity.size();
        out.add("§cTotal §a" + Math.max(loggedFound, totalFound) + "§7/§a" + total);
        out.add("§7Duplicates: §a" + totalDup);
        out.add("§7Rabbits Found: §a" + (loggedFound + totalDup));
        if (hypixelTotal > 0 && total - loggedFound > 0) out.add("§7Missing: §c" + (total - loggedFound));
        List<String> missing = new ArrayList<>();
        for (String v : c.hoppityLocationRabbits.values()) {
            String[] p = v.split("\\|");
            if (p.length == 3 && num(p[1]) < num(p[2])) missing.add(p[0] + " §7" + p[1] + "/" + p[2]);
        }
        if (!c.hoppityLocationRabbits.isEmpty()) {
            if (missing.isEmpty()) out.add("§aEnough eggs in all locations");
            else {
                out.add("§cMissing Locations:");
                missing.sort(null);
                for (String m : missing) out.add(" §c" + m);
            }
        }
        if (hypixelFound > loggedFound) out.add("§cScroll through all pages!");
        lines = out;
    }

    private static int indexOf(String r) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(r)) return i;
        return -1;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (cfg().hoppityHighlightRequirements) {
            List<Integer> hl = highlight;
            for (int idx : hl) {
                if (idx >= cs.getMenu().slots.size()) continue;
                Slot s = cs.getMenu().slots.get(idx);
                int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x8055FF55);
            }
        }
        List<String> ls = lines;
        if (ls.isEmpty()) return;
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        int pad = HudStyle.padding();
        int x = Math.max(pad, acc.getX() - w - 6 - pad);
        int y = acc.getY();
        HudStyle.panel(ctx, x - pad, y - pad, w + pad * 2, ls.size() * LINE + pad * 2);
        for (int i = 0; i < ls.size(); i++) ctx.text(mc.font, ls.get(i), x, y + i * LINE, 0xFFFFFFFF, true);
    }
}
