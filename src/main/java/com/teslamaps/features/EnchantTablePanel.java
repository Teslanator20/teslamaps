package com.teslamaps.features;

// Concept from NEU's GuiCustomEnchant / GuiCustomHex, as a side panel next to Hypixel's menus
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnchantTablePanel {

    private static final int LINE = 10;
    private static final Pattern COST = Pattern.compile("([\\d,]+) Exp Levels");
    private static final Pattern ROMAN = Pattern.compile(" [IVXL]+$");
    private static final String[] ROMANS = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    record Book(String name, int cost, boolean conflict) {}

    private static final Map<String, Book> books = new LinkedHashMap<>();
    private static List<String> lines = List.of();
    private static Screen lastScreen;
    private static int ticks;

    private EnchantTablePanel() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnchantTablePanel").section("Inventory")
                .when(() -> cfg().enchantTablePanel)
                .tick(EnchantTablePanel::tick)
                .config("Inventory", b -> {
                    b.label("Enchanting");
                    b.toggle("Enchant Table & Hex Panel", () -> cfg().enchantTablePanel, v -> cfg().enchantTablePanel = v)
                            .desc("Side panel in the Enchant Item and Hex menus: every enchant seen across the pages with cost and current level, plus what is on the item.");
                    b.toggle("  Sort By Name", () -> cfg().enchantPanelSortName, v -> cfg().enchantPanelSortName = v)
                            .desc("Off = sorted by cost.");
                    b.toggle("  Mark Conflicts", () -> cfg().enchantPanelConflicts, v -> cfg().enchantPanelConflicts = v);
                });
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = cfg();
        return c.enchantTablePanel && c.section("Inventory") && !lines.isEmpty()
                && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>;
    }

    private static boolean enchantMenu(String t) {
        return t.startsWith("Enchant Item") || t.contains("Hex");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                books.clear();
                lines = List.of();
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            if (!enchantMenu(StringUtil.stripTrim(cs.getTitle().getString()))) {
                books.clear();
                lines = List.of();
                return;
            }
        }
        if (ticks++ % 10 != 0) return;
        if (!enchantMenu(StringUtil.stripTrim(cs.getTitle().getString()))) return;
        build(cs);
    }

    private static CompoundTag tag(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    private static String enchantId(String name) {
        return name.toLowerCase(Locale.ROOT).replace("'", "").replace('-', '_').replace(' ', '_');
    }

    private static String roman(int n) {
        return n >= 0 && n < ROMANS.length ? ROMANS[n] : String.valueOf(n);
    }

    private static String title(String id) {
        StringBuilder sb = new StringBuilder();
        for (String p : id.split("_")) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static void build(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = cfg();
        CompoundTag target = null;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            if (st.is(Items.ENCHANTED_BOOK)) {
                String name = ROMAN.matcher(StringUtil.stripTrim(st.getHoverName().getString())).replaceAll("");
                if (name.isEmpty() || name.equals("Enchanted Book")) continue;
                int cost = Integer.MAX_VALUE;
                boolean conflict = false;
                for (String l : ItemUtil.loreLines(st)) {
                    if (cost == Integer.MAX_VALUE) {
                        Matcher m = COST.matcher(l);
                        if (m.find()) {
                            try { cost = Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
                        }
                    }
                    if (l.toLowerCase(Locale.ROOT).contains("conflict")) conflict = true;
                }
                books.put(name, new Book(name, cost, conflict));
                continue;
            }
            if (target == null && ItemUtil.itemUuid(st) != null) {
                CompoundTag t = tag(st);
                if (t != null && !"ENCHANTED_BOOK".equals(t.getStringOr("id", ""))) target = t;
            }
        }

        CompoundTag ench = target == null ? new CompoundTag() : target.getCompoundOrEmpty("enchantments");
        List<String> out = new ArrayList<>();
        if (!books.isEmpty()) {
            List<Book> sorted = new ArrayList<>(books.values());
            sorted.sort(c.enchantPanelSortName ? Comparator.comparing(Book::name)
                    : Comparator.comparingInt(Book::cost).thenComparing(Book::name));
            out.add("§d§lEnchants §7(" + sorted.size() + ")");
            for (Book b : sorted) {
                int cur = ench.getIntOr(enchantId(b.name()), 0);
                boolean bad = c.enchantPanelConflicts && b.conflict();
                StringBuilder sb = new StringBuilder(" ");
                sb.append(bad ? "§c" : cur > 0 ? "§a" : "§f").append(b.name());
                if (cur > 0) sb.append(" §7").append(roman(cur));
                if (b.cost() != Integer.MAX_VALUE) sb.append(" §3").append(b.cost()).append("L");
                if (bad) sb.append(" §c✖");
                out.add(sb.toString());
            }
        }
        if (target != null) {
            if (!out.isEmpty()) out.add("");
            out.add("§d§lOn Item");
            List<String> keys = new ArrayList<>(ench.keySet());
            keys.sort(null);
            out.add("§7Enchants: §f" + keys.size());
            for (String k : keys) out.add(" §9" + title(k) + " " + roman(ench.getIntOr(k, 0)));
            int potato = target.getIntOr("hot_potato_count", 0);
            out.add("§7Potato Books: §e" + Math.min(potato, 10) + "/10"
                    + (potato > 10 ? " §d+" + (potato - 10) + " Fuming" : ""));
            String reforge = target.getStringOr("modifier", "");
            out.add("§7Reforge: " + (reforge.isEmpty() ? "§8none" : "§a" + title(reforge)));
            out.add("§7Recombobulated: " + (target.getIntOr("rarity_upgrades", 0) > 0 ? "§ayes" : "§8no"));
            String scroll = target.getStringOr("power_ability_scroll", "");
            out.add("§7Power Scroll: " + (scroll.isEmpty() ? "§8none" : "§b" + title(scroll.replace("_POWER_SCROLL", ""))));
            CompoundTag gems = target.getCompoundOrEmpty("gems");
            int gemCount = 0;
            for (String k : gems.keySet()) if (!k.endsWith("_gem") && !k.equals("unlocked_slots")) gemCount++;
            out.add("§7Gemstones: §d" + gemCount);
            int stars = Math.max(target.getIntOr("upgrade_level", 0), target.getIntOr("dungeon_item_level", 0));
            if (stars > 0) out.add("§7Stars: §6" + stars);
        }
        lines = out;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        List<String> ls = lines;
        if (ls.isEmpty()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int maxLines = Math.max(3, (cs.height - 8) / LINE);
        int count = Math.min(ls.size(), maxLines);
        int w = 0;
        for (int i = 0; i < count; i++) w = Math.max(w, mc.font.width(ls.get(i)));
        int pad = HudStyle.padding();
        int x = Math.max(pad, acc.getX() - w - 6 - pad);
        int y = Math.max(pad, Math.min(acc.getY(), cs.height - count * LINE - pad));
        HudStyle.panel(ctx, x - pad, y - pad, w + pad * 2, count * LINE + pad * 2);
        for (int i = 0; i < count; i++) {
            String s = i == count - 1 && count < ls.size() ? "§8… +" + (ls.size() - count + 1) + " more" : ls.get(i);
            ctx.text(mc.font, s, x, y + i * LINE, 0xFFFFFFFF, true);
        }
    }
}
