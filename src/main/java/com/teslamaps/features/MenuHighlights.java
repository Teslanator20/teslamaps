package com.teslamaps.features;

// Ported from SkyHanni's ActiveBeaconEffect, HubSelectorCapacityHighlight, RngMeterInventory,
// MuseumCategoryHighlighter, LoadoutSlotHighlight, PowerStoneGuideFeatures, FavoritePowerStone, StatsTuning
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuHighlights {

    private record Mark(int slot, int color) {}
    private record Tip(int slot, String text) {}

    private static final int GREEN = 0x55FF55, RED = 0xFF5555, YELLOW = 0xFFFF55, GOLD = 0xFFAA00, AQUA = 0x55FFFF;

    private static final Pattern PLAYERS = Pattern.compile("Players: (\\d+)/(\\d+)");
    private static final Pattern FLOOR = Pattern.compile("Catacombs \\((.*)\\)");
    private static final Pattern POINTS = Pattern.compile("Stat has: (\\d+) points?");
    private static final Pattern LOADOUTS = Pattern.compile("\\(\\d+/\\d+\\) Loadouts");
    private static final Pattern LOCKED = Pattern.compile("Loadout \\d+ Locked");
    private static final Pattern THAUMATURGY = Pattern.compile("(?:\\(\\d/\\d\\) )?Accessory Bag Thaumaturgy");
    private static final int[] LOADOUT_ICONS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    private static List<Mark> marks = List.of();
    private static List<Tip> tips = List.of();
    private static Map<Integer, String> missingStones = Map.of();
    private static String title = "";
    private static int ticks;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MenuHighlights").section("Inventory")
                .when(() -> cfg().menuHighlights)
                .tick(MenuHighlights::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Menu Highlights");
                    b.toggle("Menu Highlights", () -> c.menuHighlights, v -> c.menuHighlights = v);
                    b.info("Colours slots in a few SkyBlock menus");
                    b.toggle("  Beacon: Active Stat Boost", () -> c.mhBeacon, v -> c.mhBeacon = v);
                    b.toggle("  Hub Selector: Player Count", () -> c.mhHubSelector, v -> c.mhHubSelector = v);
                    b.toggle("  RNG Meter: Floor, No Drop, Selected", () -> c.mhRngMeter, v -> c.mhRngMeter = v);
                    b.toggle("  Museum: Finished Categories", () -> c.mhMuseum, v -> c.mhMuseum = v);
                    b.toggle("  Loadouts: Equipped Loadout", () -> c.mhLoadout, v -> c.mhLoadout = v);
                    b.toggle("  Power Stones Guide: Missing", () -> c.mhPowerStones, v -> c.mhPowerStones = v);
                    b.toggle("    Click Opens Bazaar Search", () -> c.mhPowerStoneBazaar, v -> c.mhPowerStoneBazaar = v);
                    b.toggle("  Thaumaturgy: Shift-Click Favourites", () -> c.mhFavoritePowers, v -> c.mhFavoritePowers = v);
                    b.toggle("  Stats Tuning: Points, Template", () -> c.mhStatsTuning, v -> c.mhStatsTuning = v);
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!missingStones.isEmpty() && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void clear() {
        if (!marks.isEmpty()) marks = List.of();
        if (!tips.isEmpty()) tips = List.of();
        if (!missingStones.isEmpty()) missingStones = Map.of();
        title = "";
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { clear(); return; }
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        title = t;

        boolean beacon = c.mhBeacon && t.equals("Profile Stat Upgrades");
        boolean hub = c.mhHubSelector && t.equalsIgnoreCase("SkyBlock Hub Selector");
        boolean rng = c.mhRngMeter && t.endsWith(" RNG Meter");
        boolean museum = c.mhMuseum && t.equals("Your Museum");
        boolean loadout = c.mhLoadout && LOADOUTS.matcher(t).matches();
        boolean stones = c.mhPowerStones && t.equals("Power Stones Guide");
        boolean thaum = c.mhFavoritePowers && THAUMATURGY.matcher(t).matches();
        boolean tuning = c.mhStatsTuning && t.equals("Stats Tuning");
        if (!(beacon || hub || rng || museum || loadout || stones || thaum || tuning)) { clear(); return; }

        List<Mark> m = new ArrayList<>();
        List<Tip> tp = new ArrayList<>();
        Map<Integer, String> missing = new HashMap<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            String name = StringUtil.stripTrim(stack.getHoverName().getString());

            if (beacon && has(lore, "Active stat boost!")) m.add(new Mark(i, GREEN));
            if (hub) {
                for (String l : lore) {
                    Matcher pm = PLAYERS.matcher(StringUtil.stripTrim(l));
                    if (!pm.find()) continue;
                    int max = Math.max(1, Integer.parseInt(pm.group(2)));
                    double pct = Integer.parseInt(pm.group(1)) * 100.0 / max;
                    m.add(new Mark(i, pct >= 75 ? RED : pct >= 50 ? GOLD : pct >= 25 ? YELLOW : GREEN));
                    break;
                }
            }
            if (rng) {
                if (t.equals("Catacombs RNG Meter")) {
                    if (name.equals("RNG Meter")) {
                        for (String l : lore) {
                            Matcher fm = FLOOR.matcher(StringUtil.stripTrim(l));
                            if (fm.matches()) { tp.add(new Tip(i, fm.group(1))); break; }
                        }
                    }
                    if (contains(lore, "You don't have an RNG drop")) m.add(new Mark(i, RED));
                }
                if (has(lore, "SELECTED")) m.add(new Mark(i, YELLOW));
            }
            if (museum && has(lore, "Items Donated: 100%")) m.add(new Mark(i, GREEN));
            if (loadout && isLoadoutIcon(i) && isEquippedLoadout(stack, name, lore)) m.add(new Mark(i, GREEN));
            if (stones && has(lore, "Learned: Not Yet ✖")) {
                m.add(new Mark(i, RED));
                String stone = after(lore, "Power stone:");
                if (stone != null) missing.put(i, stone);
            }
            if (thaum && c.favoritePowers.contains(name)) m.add(new Mark(i, AQUA));
            if (tuning) {
                if (has(lore, "Currently selected!")) m.add(new Mark(i, GREEN));
                for (String l : lore) {
                    Matcher pm = POINTS.matcher(StringUtil.stripTrim(l));
                    if (pm.matches()) { tp.add(new Tip(i, pm.group(1))); break; }
                }
            }
        }
        marks = m;
        tips = tp;
        missingStones = missing;
    }

    private static boolean isLoadoutIcon(int slot) {
        for (int s : LOADOUT_ICONS) if (s == slot) return true;
        return false;
    }

    // SkyHanni: a loadout that changes nothing has no "Left-click to equip!", so it counts as worn
    private static boolean isEquippedLoadout(ItemStack stack, String name, List<String> lore) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("glass_pane") || path.equals("gray_dye") || path.equals("red_dye")) return false;
        if (LOCKED.matcher(name).matches()) return false;
        return !contains(lore, "Left-click to equip!") && !contains(lore, "You must customize this loadout");
    }

    private static boolean has(List<String> lore, String line) {
        for (String l : lore) if (StringUtil.stripTrim(l).equals(line)) return true;
        return false;
    }

    private static boolean contains(List<String> lore, String part) {
        for (String l : lore) if (StringUtil.strip(l).contains(part)) return true;
        return false;
    }

    private static String after(List<String> lore, String line) {
        for (int i = 0; i + 1 < lore.size(); i++) {
            if (StringUtil.stripTrim(lore.get(i)).equals(line)) {
                String next = StringUtil.stripTrim(lore.get(i + 1));
                return next.isEmpty() ? null : next;
            }
        }
        return null;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (marks.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Mark mk : marks) {
            if (mk.slot >= slots.size()) continue;
            Slot slot = slots.get(mk.slot);
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            int col = mk.color;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
        }
    }

    public static void renderTips(GuiGraphicsExtractor ctx) {
        if (tips.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Tip tip : tips) {
            if (tip.slot >= slots.size()) continue;
            Slot slot = slots.get(tip.slot);
            int x = acc.getX() + slot.x + 17 - mc.font.width(tip.text);
            ctx.text(mc.font, tip.text, x, acc.getY() + slot.y + 9, 0xFFFFFFFF);
        }
    }

    /** True cancels the click (favourite toggle). */
    public static boolean onSlotClick(Slot slot, ContainerInput type) {
        if (slot == null || feature == null || !feature.active()) return false;
        TeslaMapsConfig c = cfg();
        if (slot.container instanceof Inventory) return false;
        int index = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) index = cs.getMenu().slots.indexOf(slot);

        String stone = missingStones.get(index);
        if (stone != null && c.mhPowerStoneBazaar && type == ContainerInput.PICKUP) {
            String cmd = "bz " + stone;
            mc.execute(() -> { if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd); });
            return false;
        }

        if (c.mhFavoritePowers && mc.hasShiftDown() && THAUMATURGY.matcher(title).matches()) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return false;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            if (!c.favoritePowers.remove(name)) c.favoritePowers.add(name);
            TeslaMapsConfig.save();
            ticks = 4;
            return true;
        }
        return false;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem() != stack) return;
        String stone = missingStones.get(cs.getMenu().slots.indexOf(hovered));
        if (stone == null) return;
        String id = PriceManager.idForName(stone);
        Double price = id == null ? null : PriceManager.bazaarBuyOrNull(id);
        if (price == null || price <= 0) return;
        Component line = Component.literal("§79x from Bazaar: §6" + NumberUtil.formatSuffixed(price * 9));
        lines.add(Math.min(5, lines.size()), line);
    }
}
