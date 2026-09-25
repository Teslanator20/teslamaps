package com.teslamaps.features;

// Ported from SkyHanni's DungeonFinderFeatures.kt, member count from Devonian's PartyFinderCount.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyFinderHelper {

    private static final Pattern PRICE = Pattern.compile("(?i).*(?:[0-9]{2,3}K|[0-9]{1,3}M|[0-9]+\\.[0-9]M|[0-9] ?MIL).*");
    private static final Pattern CARRY = Pattern.compile("(?i).*(?:CARRY|CARY|CARRIES|CARIES|COMP|TO CATA [0-9]{2}).*");
    private static final Pattern NON_PUG = Pattern.compile("(?i).*(?:PERM|VC|DISCORD).*");
    private static final Pattern MEMBER = Pattern.compile(" (?<name>.*): (?<cls>.*?) \\(.*?(?<level>\\d+).*?\\)");
    private static final Pattern INELIGIBLE = Pattern.compile("Requires .*$|You don't meet the requirement!|Complete previous floor first!$");
    private static final Pattern FLOOR_NUM = Pattern.compile(".* (?<n>[IVX\\d]+)$");
    private static final Pattern SELECTED = Pattern.compile("Currently Selected: (?<cls>\\w+)");
    private static final String[] CLASSES = {"Archer", "Berserk", "Healer", "Mage", "Tank"};

    private static final int RED = 0x80FF5555, PURPLE = 0x80FF55FF, DARK_RED = 0x80AA0000, YELLOW = 0x80FFFF55, GREEN = 0x8055FF55;

    private static Feature feature;
    private static String selectedClass;
    private static Screen lastScreen;
    private static int kind;           // 0 none, 1 party finder, 2 gate, 3 select floor
    private static int timer;
    private static Map<Integer, Integer> highlight = Map.of();
    private static Map<Integer, String> stackTip = Map.of();
    private static Map<Integer, String> countTip = Map.of();
    private static final Map<ItemStack, List<Component>> tooltipCache = new IdentityHashMap<>();
    private static final Pattern LEVEL_REQ = Pattern.compile("Dungeon Level Required: ?(\\d+)");
    private static final Pattern PF_FLOOR = Pattern.compile("Floor: ?(?:Floor )?(\\w+)");
    private static Map<Integer, String> levelTip = Map.of();
    private static Map<Integer, String[]> missingTip = Map.of();
    private static final Map<String, Object> stats = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Object FAILED = new Object();

    private PartyFinderHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PartyFinderHelper").section("Party").when(() -> cfg().partyFinderHelper)
                .tick(PartyFinderHelper::tick)
                .config("Party", b -> {
                    b.label("Party Finder");
                    b.toggle("Party Finder Helper", () -> cfg().partyFinderHelper, v -> cfg().partyFinderHelper = v)
                            .desc("Floor as stack size, coloured groups and a class tooltip in the Party Finder.");
                    b.toggle("  Floor As Stack Size", () -> cfg().pfFloorStack, v -> cfg().pfFloorStack = v);
                    b.toggle("  Member Count", () -> cfg().pfMemberCount, v -> cfg().pfMemberCount = v);
                    b.toggle("  Mark Paid Carries (red)", () -> cfg().pfMarkCarries, v -> cfg().pfMarkCarries = v);
                    b.toggle("  Mark Non-Pugs (purple)", () -> cfg().pfMarkNonPugs, v -> cfg().pfMarkNonPugs = v);
                    b.toggle("  Mark Ineligible (dark red)", () -> cfg().pfMarkIneligible, v -> cfg().pfMarkIneligible = v);
                    b.slider("  Mark Class Level At Or Below (yellow, 0 = off)", 0f, 50f,
                            () -> (float) cfg().pfBelowClassLevel, v -> cfg().pfBelowClassLevel = Math.round(v));
                    b.toggle("  Mark Own Class Missing (green)", () -> cfg().pfMarkMissingClass, v -> cfg().pfMarkMissingClass = v);
                    b.toggle("  Coloured Class Levels", () -> cfg().pfColorLevels, v -> cfg().pfColorLevels = v);
                    b.toggle("  Show Missing Classes", () -> cfg().pfMissingClasses, v -> cfg().pfMissingClasses = v);
                    b.toggle("  Level Requirement On Head", () -> cfg().pfLevelReq, v -> cfg().pfLevelReq = v)
                            .desc("Small red dungeon level requirement in the corner of each party head.");
                    b.toggle("  Missing Classes On Head", () -> cfg().pfMissingOverlay, v -> cfg().pfMissingOverlay = v)
                            .desc("Coloured class letters (A B H M T) of the classes the party still needs.");
                    b.toggle("  Player Stats In Tooltip", () -> cfg().pfTooltipStats, v -> cfg().pfTooltipStats = v)
                            .desc("Cata level, secrets / average and S+ PB of the listed floor behind every member. Loads from the API.");
                    b.info("Your class is read when you open the Catacombs Gate (Mort)");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (kind == 1 && feature.active()) onTooltip(stack, lines);
        });
    }

    private static int kindOf(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?>)) return 0;
        String t = StringUtil.stripTrim(s.getTitle().getString());
        if (t.equals("Party Finder")) return 1;
        if (t.equals("Catacombs Gate")) return 2;
        if (t.equals("Select Floor")) return 3;
        return 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            kind = kindOf(mc.screen);
            timer = 0;
            tooltipCache.clear();
            highlight = Map.of();
            stackTip = Map.of();
            countTip = Map.of();
            levelTip = Map.of();
            missingTip = Map.of();
        }
        if (kind == 0 || timer-- > 0) return;
        timer = 5;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        Map<Integer, Integer> hl = new HashMap<>();
        Map<Integer, String> tips = new HashMap<>();
        Map<Integer, String> counts = new HashMap<>();
        Map<Integer, String> levels0 = new HashMap<>();
        Map<Integer, String[]> missing0 = new HashMap<>();
        TeslaMapsConfig c = cfg();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            List<String> lore = ItemUtil.loreLines(stack);
            if (kind == 2) {
                for (String l : lore) {
                    Matcher m = SELECTED.matcher(l.trim());
                    if (m.find()) selectedClass = m.group("cls");
                }
                if (name.startsWith("The Catacombs") || name.contains("MM The Catacombs") || name.contains("Master Mode The Catacombs")) {
                    String f = floorName(name, name);
                    if (f != null) tips.put(slot.index, f);
                }
            } else if (kind == 3) {
                if (name.equals("Any")) tips.put(slot.index, "A");
                else if (name.endsWith("Entrance")) tips.put(slot.index, "E");
                else if (name.startsWith("Floor ")) {
                    int n = floorNumber(name);
                    if (n > 0) tips.put(slot.index, String.valueOf(n));
                }
            } else if (name.endsWith("'s Party")) {
                String floor = null, dungeon = null, note = "";
                List<Integer> levels = new ArrayList<>();
                List<String> classes = new ArrayList<>();
                boolean ineligible = false;
                for (String l : lore) {
                    String t = l.trim();
                    if (floor == null && t.startsWith("Floor")) floor = t;
                    else if (dungeon == null && t.startsWith("Dungeon: ")) dungeon = t;
                    else if (t.startsWith("Note: ")) note += " " + t.toUpperCase(Locale.ROOT);
                    if (INELIGIBLE.matcher(t).matches()) ineligible = true;
                    Matcher lr = LEVEL_REQ.matcher(t);
                    if (lr.find()) levels0.put(slot.index, "§c" + lr.group(1));
                    Matcher m = MEMBER.matcher(l);
                    if (m.matches()) {
                        levels.add(Integer.parseInt(m.group("level")));
                        classes.add(m.group("cls").trim());
                    }
                }
                if (floor != null && dungeon != null) {
                    String f = floorName(floor, dungeon);
                    if (f != null) tips.put(slot.index, f);
                }
                counts.put(slot.index, "§e" + classes.size());
                if (c.pfMissingOverlay) {
                    StringBuilder a = new StringBuilder(), bb = new StringBuilder();
                    int n = 0;
                    for (String cl : CLASSES) {
                        if (classes.stream().anyMatch(x -> x.equalsIgnoreCase(cl))) continue;
                        (n++ < 2 ? a : bb).append(shortClass(cl));
                    }
                    if (n > 0) missing0.put(slot.index, new String[]{a.toString(), bb.toString()});
                }
                Integer col = null;
                if (c.pfMarkIneligible && ineligible) col = DARK_RED;
                else if (c.pfMarkCarries && PRICE.matcher(note).matches() && CARRY.matcher(note).matches()) col = RED;
                else if (c.pfMarkNonPugs && NON_PUG.matcher(note).matches()) col = PURPLE;
                else if (c.pfBelowClassLevel > 0 && levels.stream().anyMatch(v -> v <= c.pfBelowClassLevel)) col = YELLOW;
                else if (c.pfMarkMissingClass && selectedClass != null
                        && classes.stream().noneMatch(s -> s.equalsIgnoreCase(selectedClass))) col = GREEN;
                if (col != null) hl.put(slot.index, col);
            }
        }
        highlight = hl;
        stackTip = tips;
        countTip = counts;
        levelTip = levels0;
        missingTip = missing0;
    }

    private static String shortClass(String cls) {
        return switch (cls) {
            case "Archer" -> "§4§lA";
            case "Berserk" -> "§6§lB";
            case "Healer" -> "§5§lH";
            case "Mage" -> "§b§lM";
            default -> "§a§lT";
        };
    }

    private static String floorName(String floor, String dungeon) {
        if (floor.endsWith("Entrance")) return "E";
        int n = floorNumber(floor);
        if (n <= 0) return null;
        boolean master = dungeon.contains("MM The Catacombs") || dungeon.contains("Master Mode");
        return (master ? "M" : "F") + n;
    }

    private static int floorNumber(String s) {
        Matcher m = FLOOR_NUM.matcher(s);
        if (!m.matches()) return -1;
        String r = m.group("n");
        if (Character.isDigit(r.charAt(0))) return Integer.parseInt(r);
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    /** Behind the item icon, from SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (kind != 1 || highlight.isEmpty() || feature == null || !feature.active()) return;
        if (slot.container instanceof Inventory) return;
        Integer col = highlight.get(slot.index);
        if (col != null) ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, col);
    }

    /** On top of the items, from ContainerOverlays. */
    public static void render(GuiGraphicsExtractor ctx) {
        if (kind == 0 || feature == null || !feature.active()) return;
        TeslaMapsConfig c = cfg();
        boolean extra = kind == 1 && (c.pfLevelReq || c.pfMissingOverlay);
        boolean slotText = (c.pfFloorStack || c.pfMemberCount) && SlotText.shown(SlotText.PARTY_FINDER);
        if (!slotText && !extra) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (extra) drawExtras(ctx, mc, slot.index, sx, sy, c);
            if (!slotText) continue;
            if (c.pfFloorStack) {
                String t = stackTip.get(slot.index);
                if (t != null) ctx.text(mc.font, t, sx + 17 - mc.font.width(t), sy + 9, 0xFFFFFFFF, true);
            }
            if (c.pfMemberCount && kind == 1) {
                String t = countTip.get(slot.index);
                if (t != null) ctx.text(mc.font, t, sx, sy, 0xFFFFFFFF, true);
            }
        }
    }

    private static void drawExtras(GuiGraphicsExtractor ctx, Minecraft mc, int index, int sx, int sy, TeslaMapsConfig c) {
        String lvl = c.pfLevelReq ? levelTip.get(index) : null;
        String[] miss = c.pfMissingOverlay ? missingTip.get(index) : null;
        if (lvl == null && miss == null) return;
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(sx, sy);
        if (lvl != null) {
            pose.pushMatrix();
            pose.translate(16f - mc.font.width(lvl) * 0.6f, 0f);
            pose.scale(0.6f, 0.6f);
            ctx.text(mc.font, lvl, 0, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        if (miss != null) {
            pose.pushMatrix();
            pose.translate(0f, 4f);
            pose.scale(0.65f, 0.65f);
            if (!miss[1].isEmpty()) {
                ctx.text(mc.font, miss[0], 0, 0, 0xFFFFFFFF, true);
                ctx.text(mc.font, miss[1], 0, 9, 0xFFFFFFFF, true);
            } else ctx.text(mc.font, miss[0], 0, 9, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        pose.popMatrix();
    }

    private static String statsFor(String name, int floor, boolean master) {
        String key = name.toLowerCase(Locale.ROOT);
        Object got = stats.get(key);
        if (got == FAILED) return " §c(Failed)";
        if (got instanceof com.teslamaps.api.DungeonData dd) {
            StringBuilder sb = new StringBuilder(" §b(§6").append(dd.getCatacombsLevel()).append("§b)");
            int runs = dd.getTotalRuns();
            String avg = runs > 0 ? String.format(Locale.US, "%.2f", dd.getSecretsFound() / (double) runs) : "0";
            sb.append(" §8[§a").append(dd.getSecretsFound()).append("§8/§b").append(avg).append("§8]");
            if (floor > 0) {
                var fd = (master ? dd.getMasterFloors() : dd.getNormalFloors()).get(String.valueOf(floor));
                String pb = fd != null && fd.getFastestSPlusTime() > 0 ? fd.getFormattedSPlusTime() : "N/A";
                sb.append(" §8[§9").append(pb).append("§8]");
            }
            return sb.toString();
        }
        if (pending.size() < 5 && pending.add(key)) {
            com.teslamaps.api.HypixelApi.nameToUuid(name)
                    .thenCompose(uuid -> uuid == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                            : com.teslamaps.api.HypixelApi.getProfiles(uuid))
                    .handle((resp, err) -> {
                        Object v = FAILED;
                        try {
                            com.google.gson.JsonObject member = resp != null && resp.has("profiles")
                                    ? selectedMember(resp.getAsJsonObject("profiles")) : null;
                            if (member != null) v = new com.teslamaps.api.DungeonData(member);
                        } catch (Exception ignored) {}
                        if (stats.size() > 200) stats.clear();
                        stats.put(key, v);
                        pending.remove(key);
                        Minecraft.getInstance().execute(tooltipCache::clear);
                        return null;
                    });
        }
        return " §7(Loading...)";
    }

    private static com.google.gson.JsonObject selectedMember(com.google.gson.JsonObject profiles) {
        com.google.gson.JsonObject fallback = null;
        for (var e : profiles.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            var p = e.getValue().getAsJsonObject();
            var data = p.has("data") && p.get("data").isJsonObject() ? p.getAsJsonObject("data") : null;
            if (data == null) continue;
            if (fallback == null) fallback = data;
            if (p.has("current") && p.get("current").getAsBoolean()) return data;
        }
        return fallback;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        if (!c.pfColorLevels && !c.pfMissingClasses && !c.pfTooltipStats) return;
        if (!StringUtil.stripTrim(stack.getHoverName().getString()).endsWith("'s Party")) return;
        List<Component> cached = tooltipCache.get(stack);
        if (cached == null) {
            cached = buildTooltip(lines);
            if (tooltipCache.size() > 128) tooltipCache.clear();
            tooltipCache.put(stack, cached);
        }
        lines.clear();
        lines.addAll(cached);
    }

    private static List<Component> buildTooltip(List<Component> lines) {
        TeslaMapsConfig c = cfg();
        List<Component> out = new ArrayList<>(lines.size() + 2);
        List<String> missing = new ArrayList<>(List.of(CLASSES));
        boolean isParty = false;
        int floor = 0;
        boolean master = false;
        for (Component line : lines) {
            String s = StringUtil.strip(line.getString());
            String st0 = s.trim();
            if (st0.startsWith("Dungeon: ")) {
                isParty = true;
                if (st0.contains("Master Mode")) master = true;
            }
            Matcher fm = PF_FLOOR.matcher(st0);
            if (st0.startsWith("Floor") && fm.find()) {
                String r = fm.group(1);
                floor = r.equals("Entrance") ? 0 : Math.max(0, floorNumber("x " + r));
            }
            Matcher m = MEMBER.matcher(s);
            if (!m.matches()) { out.add(line); continue; }
            String cls = m.group("cls").trim();
            int level = Integer.parseInt(m.group("level"));
            missing.removeIf(x -> x.equalsIgnoreCase(cls));
            String st = c.pfTooltipStats ? statsFor(m.group("name").trim(), floor, master) : null;
            if (!c.pfColorLevels) {
                out.add(st == null ? line : line.copy().append(Component.literal(st)));
                continue;
            }
            MutableComponent nl = Component.literal(" " + m.group("name")).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(cls + " ").withStyle(ChatFormatting.YELLOW))
                    .append(levelComponent(level));
            if (st != null) nl.append(Component.literal(st));
            out.add(nl);
        }
        if (c.pfMissingClasses && isParty) {
            out.add(Component.empty());
            MutableComponent miss = Component.literal("Missing: ").withStyle(ChatFormatting.RED);
            for (int i = 0; i < missing.size(); i++) {
                String cl = missing.get(i);
                boolean own = cl.equalsIgnoreCase(selectedClass);
                miss.append(Component.literal(cl).withStyle(own ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                if (i < missing.size() - 1) miss.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            if (missing.isEmpty()) miss.append(Component.literal("none").withStyle(ChatFormatting.GRAY));
            out.add(miss);
        }
        return out;
    }

    private static Component levelComponent(int level) {
        ChatFormatting f = level >= 45 ? ChatFormatting.RED : level >= 40 ? ChatFormatting.GOLD
                : level >= 35 ? ChatFormatting.DARK_PURPLE : level >= 30 ? ChatFormatting.BLUE
                : level >= 25 ? ChatFormatting.AQUA : level >= 20 ? ChatFormatting.DARK_GREEN
                : level >= 15 ? ChatFormatting.GREEN : level >= 10 ? ChatFormatting.YELLOW
                : level >= 5 ? ChatFormatting.WHITE : ChatFormatting.GRAY;
        MutableComponent m = Component.literal(String.valueOf(level)).withStyle(f);
        if (level >= 50) m.withStyle(ChatFormatting.BOLD);
        return m;
    }
}
