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

    public static final String[] RIGHT_CLICK_MODES = {"Off", "Copy Leader", "Leave Party"};
    private static final Pattern LEADER = Pattern.compile("^(\\w{1,16})'s Party$");

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
                    b.dropdown("  Right-Click Party", RIGHT_CLICK_MODES, () -> cfg().pfRightClickMode, v -> cfg().pfRightClickMode = v)
                            .desc("Right click on a party: copy the leader's name, or leave your current party (/p leave)");
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
        }
        if (kind == 0 || timer-- > 0) return;
        timer = 5;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        Map<Integer, Integer> hl = new HashMap<>();
        Map<Integer, String> tips = new HashMap<>();
        Map<Integer, String> counts = new HashMap<>();
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

    /** Right click on a party head, from HandledScreenClickMixin.slotClicked. */
    public static boolean rightClick(Slot slot, int button, net.minecraft.world.inventory.ContainerInput type) {
        if (button != 1 || type != net.minecraft.world.inventory.ContainerInput.PICKUP || kind != 1 || slot == null) return false;
        if (feature == null || !feature.active() || "Off".equals(cfg().pfRightClickMode)) return false;
        if (slot.container instanceof Inventory || slot.index >= 45) return false;
        return rightClickAction(slot.getItem());
    }

    /** Copy leader / leave party for one party head; false when off or not a party. */
    public static boolean rightClickAction(ItemStack stack) {
        if (feature == null || !feature.active() || "Off".equals(cfg().pfRightClickMode)) return false;
        if (!stack.is(net.minecraft.world.item.Items.PLAYER_HEAD)) return false;
        Matcher m = LEADER.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (!m.matches()) return false;
        Minecraft mc = Minecraft.getInstance();
        if ("Leave Party".equals(cfg().pfRightClickMode)) {
            ChatQueue.command("p leave");
        } else {
            mc.keyboardHandler.setClipboard(m.group(1));
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied leader name §a" + m.group(1)));
        }
        return true;
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
        if (!c.pfFloorStack && !c.pfMemberCount || !SlotText.shown(SlotText.PARTY_FINDER)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
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

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        if (!c.pfColorLevels && !c.pfMissingClasses) return;
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
        for (Component line : lines) {
            String s = StringUtil.strip(line.getString());
            if (s.trim().startsWith("Dungeon: ")) isParty = true;
            Matcher m = MEMBER.matcher(s);
            if (!m.matches()) { out.add(line); continue; }
            String cls = m.group("cls").trim();
            int level = Integer.parseInt(m.group("level"));
            missing.removeIf(x -> x.equalsIgnoreCase(cls));
            if (!c.pfColorLevels) { out.add(line); continue; }
            MutableComponent nl = Component.literal(" " + m.group("name")).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(cls + " ").withStyle(ChatFormatting.YELLOW))
                    .append(levelComponent(level));
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
