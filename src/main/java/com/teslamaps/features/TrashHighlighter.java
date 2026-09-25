package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.TrashFilter;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TrashHighlighter {

    public static final String[] TARGETS = {"ID", "Name", "Lore"};
    public static final String[] MODES = {"Equals", "Contains", "Regex"};
    public static final String[] STYLES = {"Fill", "Outline"};

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();
    private static final Map<String, Pattern> regexes = new HashMap<>();
    private static long cacheAt;

    public static List<TrashFilter> defaults() {
        List<TrashFilter> l = new ArrayList<>();
        for (String id : new String[]{"DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE",
                "MIMIC_FRAGMENT", "ENCHANTED_ROTTEN_FLESH", "ENCHANTED_BONE", "CRYPT_BOW", "CRYPT_DREADLORD_SWORD",
                "MACHINE_GUN_BOW", "EARTH_SHARD", "SUPER_CLEAVER", "INFLATABLE_JERRY"}) {
            l.add(new TrashFilter("ID", "Equals", id));
        }
        for (String part : new String[]{"SKELETON_GRUNT_", "SKELETON_SOLDIER_", "SKELETON_MASTER_", "ZOMBIE_SOLDIER_",
                "ZOMBIE_KNIGHT_", "ZOMBIE_COMMANDER_", "SNIPER_"}) {
            l.add(new TrashFilter("ID", "Contains", part));
        }
        return l;
    }

    public static void invalidate() {
        cache.clear();
        regexes.clear();
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.trashHighlighter && c.sellableHighlighter && c.section("Inventory")
                && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs)) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.trashFilters.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - cacheAt > 2000 || cache.size() > 2000) { cache.clear(); cacheAt = now; }
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int col = TeslaMapsConfig.parseColor(c.colorTrash);
        boolean outline = "Outline".equals(c.trashHighlightStyle);
        for (Slot slot : cs.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty() || !isTrash(st, c.trashFilters)) continue;
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            if (outline) {
                int o = col | 0xFF000000;
                ctx.fill(x, y, x + 16, y + 1, o);
                ctx.fill(x, y + 15, x + 16, y + 16, o);
                ctx.fill(x, y + 1, x + 1, y + 15, o);
                ctx.fill(x + 15, y + 1, x + 16, y + 15, o);
            } else {
                ctx.fill(x, y, x + 16, y + 16, col);
            }
        }
    }

    private static boolean isTrash(ItemStack st, List<TrashFilter> filters) {
        Boolean b = cache.get(st);
        if (b != null) return b;
        boolean v = compute(st, filters);
        cache.put(st, v);
        return v;
    }

    private static boolean compute(ItemStack st, List<TrashFilter> filters) {
        String id = null, name = null;
        List<String> lore = null;
        for (TrashFilter f : filters) {
            if (f.value == null || f.value.isEmpty()) continue;
            switch (f.target) {
                case "Name" -> {
                    if (name == null) name = StringUtil.stripTrim(st.getHoverName().getString());
                    if (test(f, name)) return true;
                }
                case "Lore" -> {
                    if (lore == null) lore = ItemUtil.loreLines(st);
                    for (String l : lore) if (test(f, StringUtil.stripTrim(l))) return true;
                }
                default -> {
                    if (id == null) { id = ItemUtil.skyblockId(st); if (id == null) id = ""; }
                    if (!id.isEmpty() && test(f, id)) return true;
                }
            }
        }
        return false;
    }

    private static boolean test(TrashFilter f, String s) {
        return switch (f.mode) {
            case "Equals" -> s.equalsIgnoreCase(f.value);
            case "Regex" -> {
                Pattern p = regexes.computeIfAbsent(f.value, TrashHighlighter::compile);
                yield p != null && p.matcher(s).find();
            }
            default -> s.toLowerCase().contains(f.value.toLowerCase());
        };
    }

    private static Pattern compile(String r) {
        try {
            return Pattern.compile(r);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    public static boolean validRegex(String r) {
        return compile(r) != null;
    }
}
