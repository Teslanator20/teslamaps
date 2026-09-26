package com.teslamaps.features;

// After SkyHanni's combat/BestiaryData.kt + BestiaryApi.kt, percent on items like SkyblockExtras
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bestiary menu: percent on every family, a sortable panel of the page's families and categories. */
public final class BestiaryMenuData {

    public static final String[] SORTS = {
            "Kills To Next (low)", "Kills To Next (high)", "Kills To Max (low)", "Kills To Max (high)",
            "% To Next", "% To Max", "Total Kills (low)", "Total Kills (high)"};

    private static final Pattern NAME = Pattern.compile("^(?<name>.+?)(?: (?<tier>[IVXLC]+|\\d+))?$");
    private static final Pattern KILLS = Pattern.compile("^Kills: ([\\d,]+)$");
    private static final Pattern TIER = Pattern.compile("^Progress to Tier [\\dIVXLC]+: ([\\d.]+)%$");
    private static final Pattern OVERALL = Pattern.compile("^Overall Progress: ([\\d.]+)%( \\(MAX!\\))?$");
    private static final Pattern BAR = Pattern.compile("([\\dkKmMbB,.]+)/([\\dkKmMbB,.]+)$");
    private static final Pattern FOUND = Pattern.compile("^Families Found:? (\\d+)/(\\d+)");
    private static final Pattern DONE = Pattern.compile("^Families Completed:? (\\d+)/(\\d+)");
    private static final String LOCKED = "You haven't unlocked this Family yet!";
    private static final String HIDDEN = "Overall Progress: HIDDEN";

    private record Family(String name, int tier, boolean locked, boolean maxed, boolean hidden, long kills,
                          long tierCur, long tierNeed, double tierPct, long maxCur, long maxNeed, double maxPct) {
        long toNext() { return Math.max(0, tierNeed - tierCur); }
        long toMax() { return Math.max(0, maxNeed - maxCur); }
    }

    private record Category(String name, int found, int total, int done) {}

    private static final Family NONE = new Family("", 0, false, false, false, 0, 0, 0, 0, 0, 0, 0);
    private static final IdentityHashMap<ItemStack, Family> famCache = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, Category> catCache = new IdentityHashMap<>();
    private static final Category NO_CAT = new Category("", 0, 0, 0);

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inMenu;
    private static int ticks, panelW;
    private static String lastQuery = "";
    private static List<String> lines = List.of();
    private static List<List<Component>> hovers = List.of();
    private static String[] slotText = new String[0];

    private BestiaryMenuData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("BestiaryMenuData").section("Combat").when(() -> cfg().bestiaryMenuData)
                .tick(BestiaryMenuData::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Bestiary Menu");
                    b.toggle("Bestiary Menu Overview", () -> c.bestiaryMenuData, v -> c.bestiaryMenuData = v)
                            .desc("In the Bestiary menu: percent on every family, and a panel with the page's categories and "
                                    + "families, sorted the way you pick. Type in the container searchbar to filter it")
                            .tags("bestiary", "family", "percent", "sort", "search");
                    b.dropdown("  Sort", SORTS, () -> c.bestiaryMenuSort, v -> c.bestiaryMenuSort = v);
                    b.toggle("  Percent On Items", () -> c.bestiaryMenuPercent, v -> c.bestiaryMenuPercent = v);
                    b.toggle("    Percent To Max Instead Of Next Tier", () -> c.bestiaryMenuPercentMax, v -> c.bestiaryMenuPercentMax = v);
                    b.toggle("  Hide Maxed Families", () -> c.bestiaryMenuHideMaxed, v -> c.bestiaryMenuHideMaxed = v);
                    b.toggle("  Tiers As Numbers (1, 2, 3)", () -> c.bestiaryMenuArabic, v -> c.bestiaryMenuArabic = v);
                    b.toggle("  Short Numbers (1.2k)", () -> c.bestiaryMenuShortNumbers, v -> c.bestiaryMenuShortNumbers = v);
                });
    }

    private static boolean isMenu(String title) {
        return title.contains("Bestiary") || title.startsWith("Fishing ➜") || title.equals("Search Results");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) { lastScreen = null; inMenu = false; lines = List.of(); }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            lines = List.of();
            slotText = new String[0];
            inMenu = isMenu(StringUtil.stripTrim(cs.getTitle().getString()));
        }
        if (!inMenu) return;
        String q = Searchbar.query();
        if (ticks++ % 10 != 0 && q.equals(lastQuery)) return;
        lastQuery = q;
        build(cs, q.toLowerCase(Locale.ROOT));
    }

    private static void build(AbstractContainerScreen<?> cs, String query) {
        TeslaMapsConfig c = cfg();
        if (famCache.size() > 256) famCache.clear();
        if (catCache.size() > 256) catCache.clear();
        List<Slot> slots = cs.getMenu().slots;
        String[] text = new String[slots.size()];
        List<Family> fams = new ArrayList<>();
        List<Category> cats = new ArrayList<>();
        boolean hidden = false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Category cat = catCache.computeIfAbsent(stack, BestiaryMenuData::parseCategory);
            if (cat != NO_CAT) {
                cats.add(cat);
                continue;
            }
            Family f = famCache.computeIfAbsent(stack, BestiaryMenuData::parseFamily);
            if (f == NONE) continue;
            fams.add(f);
            hidden |= f.hidden;
            if (c.bestiaryMenuPercent && !f.locked) text[i] = percentText(f, c.bestiaryMenuPercentMax);
        }
        slotText = text;

        List<String> out = new ArrayList<>();
        List<List<Component>> hov = new ArrayList<>();
        if (!cats.isEmpty()) {
            out.add("§7Categories");
            hov.add(List.of());
            for (Category cat : cats) {
                if (!query.isEmpty() && !cat.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                String info = cat.done == cat.total ? "§c§lCompleted!"
                        : cat.found == cat.total ? "§b" + cat.done + "§7/§b" + cat.total + " §7completed"
                        : "§b" + cat.found + "§7/§b" + cat.total + " §7found, §b" + cat.done + "§7/§b" + cat.total + " §7completed";
                out.add(" §7- §f" + cat.name + "§7: " + info);
                hov.add(List.of());
            }
        }
        if (!fams.isEmpty()) {
            out.add("§7Bestiary Data");
            hov.add(List.of());
            if (hidden) {
                out.add(" §cOverall Progress is hidden - turn it on");
                out.add(" §cwith the Eye of Ender in this menu");
                hov.add(List.of());
                hov.add(List.of());
            }
            fams.sort(comparator(c.bestiaryMenuSort));
            for (Family f : fams) {
                if (!query.isEmpty() && !f.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (f.locked) {
                    out.add(" §7- §f" + f.name + "§c: Not unlocked!");
                    hov.add(List.of());
                    continue;
                }
                if (f.maxed && c.bestiaryMenuHideMaxed) continue;
                out.add(line(f, c));
                hov.add(hover(f, c));
            }
        }
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : out) w = Math.max(w, font.width(s));
        panelW = w;
        hovers = hov;
        lines = out;
    }

    private static Comparator<Family> comparator(String sort) {
        Comparator<Family> cmp = switch (sort) {
            case "Kills To Next (high)" -> Comparator.comparingLong(Family::toNext).reversed();
            case "Kills To Max (low)" -> Comparator.comparingLong(Family::toMax);
            case "Kills To Max (high)" -> Comparator.comparingLong(Family::toMax).reversed();
            case "% To Next" -> Comparator.comparingDouble(Family::tierPct).reversed();
            case "% To Max" -> Comparator.comparingDouble(Family::maxPct).reversed();
            case "Total Kills (low)" -> Comparator.comparingLong(Family::kills);
            case "Total Kills (high)" -> Comparator.comparingLong(Family::kills).reversed();
            default -> Comparator.comparingLong(Family::toNext);
        };
        // locked and maxed families sink to the bottom
        return Comparator.comparingInt((Family f) -> f.locked ? 2 : f.maxed ? 1 : 0).thenComparing(cmp);
    }

    private static String line(Family f, TeslaMapsConfig c) {
        String head = " §7- §f" + f.name + " §e" + tier(f.tier, c) + "§7: ";
        if (f.maxed) return head + "§c§lMAXED! §7(§b" + num(f.kills, c) + "§7 kills)";
        return head + switch (c.bestiaryMenuSort) {
            case "Kills To Max (low)", "Kills To Max (high)" -> "§6" + num(f.toMax(), c) + " §7kills to max";
            case "% To Max" -> "§7(§b" + num(f.maxCur, c) + "§7/§b" + num(f.maxNeed, c) + "§7) §a" + pct(f.maxPct) + "§6%";
            case "% To Next" -> "§7(§b" + num(f.tierCur, c) + "§7/§b" + num(f.tierNeed, c) + "§7) §a" + pct(f.tierPct)
                    + "§6% §ato " + tier(f.tier + 1, c);
            case "Total Kills (low)", "Total Kills (high)" -> "§6" + num(f.kills, c) + " §7total kills";
            default -> "§6" + num(f.toNext(), c) + " §7kills to " + tier(f.tier + 1, c);
        };
    }

    private static List<Component> hover(Family f, TeslaMapsConfig c) {
        List<Component> l = new ArrayList<>();
        l.add(Component.literal("§6Name: §b" + f.name));
        l.add(Component.literal("§6Tier: §b" + tier(f.tier, c)));
        l.add(Component.literal("§6Total Kills: §b" + num(f.kills, c)));
        if (!f.maxed) {
            l.add(Component.literal("§6Kills to next tier: §b" + num(f.toNext(), c)
                    + " §7(" + num(f.tierCur, c) + "/" + num(f.tierNeed, c) + ", " + pct(f.tierPct) + "%)"));
        }
        if (!f.hidden) {
            l.add(Component.literal("§6Kills to max: §b" + num(f.toMax(), c)
                    + " §7(" + num(f.maxCur, c) + "/" + num(f.maxNeed, c) + ", " + pct(f.maxPct) + "%)"));
        }
        return l;
    }

    private static String percentText(Family f, boolean toMax) {
        if (f.maxed) return "§6MAX";
        double p = toMax ? f.maxPct : f.tierPct;
        if (toMax && f.hidden) return null;
        return (p >= 75 ? "§a" : p >= 40 ? "§e" : "§c") + (int) Math.floor(p) + "%";
    }

    private static Family parseFamily(ItemStack stack) {
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return NONE;
        Matcher n = NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (!n.matches()) return NONE;
        String name = n.group("name").trim();
        int tier = n.group("tier") == null ? 0 : tierValue(n.group("tier"));
        boolean family = false, locked = false, maxed = false, hidden = false;
        long kills = 0, tierCur = 0, tierNeed = 0, maxCur = 0, maxNeed = 0;
        double tierPct = 0, maxPct = 0;
        int barFor = 0; // 1 = tier bar next, 2 = overall bar next
        for (String raw : lore) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.contains(LOCKED)) { family = true; locked = true; break; }
            if (line.equals(HIDDEN)) { family = true; hidden = true; barFor = 0; continue; }
            Matcher m;
            if ((m = KILLS.matcher(line)).matches()) { kills = (long) amount(m.group(1)); continue; }
            if ((m = TIER.matcher(line)).matches()) { family = true; tierPct = parse(m.group(1)); barFor = 1; continue; }
            if ((m = OVERALL.matcher(line)).matches()) {
                family = true;
                maxPct = parse(m.group(1));
                maxed = m.group(2) != null || maxPct >= 100;
                barFor = 2;
                continue;
            }
            if (barFor != 0 && (m = BAR.matcher(line)).find()) {
                long a = (long) amount(m.group(1)), b = (long) amount(m.group(2));
                if (barFor == 1) { tierCur = a; tierNeed = b; } else { maxCur = a; maxNeed = b; }
                barFor = 0;
            }
        }
        if (!family) return NONE;
        if (!hidden && maxCur > 0) kills = Math.max(kills, maxCur);
        return new Family(name, tier, locked, maxed, hidden, kills, tierCur, tierNeed, tierPct, maxCur, maxNeed, maxPct);
    }

    private static Category parseCategory(ItemStack stack) {
        int found = -1, total = 0, done = -1;
        for (String raw : ItemUtil.loreLines(stack)) {
            String line = raw.trim();
            Matcher m;
            if ((m = FOUND.matcher(line)).find()) { found = Integer.parseInt(m.group(1)); total = Integer.parseInt(m.group(2)); }
            else if ((m = DONE.matcher(line)).find()) { done = Integer.parseInt(m.group(1)); total = Integer.parseInt(m.group(2)); }
        }
        if (done < 0 || total <= 0) return NO_CAT;
        if (found < 0) found = total;
        return new Category(StringUtil.stripTrim(stack.getHoverName().getString()), found, total, done);
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private static double amount(String s) {
        s = s.replace(",", "");
        if (s.isEmpty()) return 0;
        double mult = switch (Character.toLowerCase(s.charAt(s.length() - 1))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            default -> 1;
        };
        if (mult > 1) s = s.substring(0, s.length() - 1);
        return parse(s) * mult;
    }

    private static int tierValue(String s) {
        if (Character.isDigit(s.charAt(0))) return Integer.parseInt(s);
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> 0;
            };
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }

    private static final String[] ROMAN_S = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    private static final int[] ROMAN_V = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

    private static String tier(int t, TeslaMapsConfig c) {
        if (c.bestiaryMenuArabic || t <= 0) return String.valueOf(t);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_V.length; i++) while (t >= ROMAN_V[i]) { sb.append(ROMAN_S[i]); t -= ROMAN_V[i]; }
        return sb.toString();
    }

    private static String num(long v, TeslaMapsConfig c) {
        if (!c.bestiaryMenuShortNumbers || v < 1000) return String.format(Locale.ROOT, "%,d", v);
        if (v < 1_000_000) return trim(v / 1e3) + "k";
        if (v < 1_000_000_000) return trim(v / 1e6) + "M";
        return trim(v / 1e9) + "B";
    }

    private static String trim(double d) {
        return d >= 100 ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    private static String pct(double p) {
        return String.format(Locale.ROOT, "%.1f", p);
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!inMenu || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("BestiaryMenuData.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String[] text = slotText;
        List<Slot> slots = cs.getMenu().slots;
        var font = mc.font;
        for (int i = 0; i < text.length && i < slots.size(); i++) {
            if (text[i] == null) continue;
            Slot s = slots.get(i);
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(acc.getX() + s.x, acc.getY() + s.y);
            pose.scale(0.6f, 0.6f);
            ctx.text(font, text[i], 0, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        List<String> l = lines;
        if (!l.isEmpty()) {
            int lh = font.lineHeight + 1;
            int x = acc.getX() + acc.getImageWidth() + 8;
            int y = acc.getY() + 4;
            com.teslamaps.render.gui.HudStyle.panel(ctx, x - 4, y - 4, panelW + 8, l.size() * lh + 6);
            List<List<Component>> hov = hovers;
            for (int i = 0; i < l.size(); i++) {
                int ly = y + i * lh;
                ctx.text(font, l.get(i), x, ly, 0xFFFFFFFF);
                if (i < hov.size() && !hov.get(i).isEmpty() && mouseX >= x && mouseX < x + panelW && mouseY >= ly && mouseY < ly + lh) {
                    ctx.setComponentTooltipForNextFrame(font, hov.get(i), mouseX, mouseY);
                }
            }
        }
        Perf.end();
    }
}
