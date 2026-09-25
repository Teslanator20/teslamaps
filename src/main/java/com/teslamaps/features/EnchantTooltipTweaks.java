package com.teslamaps.features;

// Duplicate fix ported from Skysoft's DuplicateEnchantmentTooltipFix.kt, renames from Skytils' EnchantNames (concept)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.TextFieldsScreen;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class EnchantTooltipTweaks {

    private static final int RENAME_ROWS = 8;

    private static List<String[]> rules = List.of();
    private static List<String> rulesFrom = List.of();

    private EnchantTooltipTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnchantTooltipTweaks")
                .config("Tooltips & Value", b -> {
                    b.label("Enchant Tooltip Extras");
                    b.toggle("Remove Duplicate Enchant Lines", () -> cfg().duplicateEnchantFix, v -> cfg().duplicateEnchantFix = v)
                            .desc("Drops the vanilla enchantment line when the same line is already in the Hypixel lore.");
                    b.toggle("Rename Enchants", () -> cfg().enchantRename, v -> cfg().enchantRename = v)
                            .desc("Own names for enchants in tooltips, level and colour stay.");
                    b.button("  Edit Enchant Names", EnchantTooltipTweaks::openEditor);
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            TeslaMapsConfig c = cfg();
            if (!c.duplicateEnchantFix && !c.enchantRename) return;
            if (lines.size() < 2) return;
            com.teslamaps.debug.Perf.begin("EnchantTooltipTweaks");
            try {
                if (c.duplicateEnchantFix && SkyblockUtils.isInSkyblock()) removeDuplicates(stack, lines);
                if (c.enchantRename && !ItemUtil.skyblockId(stack).isEmpty()) rename(lines, c);
            } finally {
                com.teslamaps.debug.Perf.end();
            }
        });
    }

    private static void removeDuplicates(ItemStack stack, List<Component> tooltip) {
        if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).shows(DataComponents.ENCHANTMENTS)) return;
        ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
        if (ench == null || ench.isEmpty()) return;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return;
        Set<String> loreLines = new HashSet<>();
        for (Component l : lore.lines()) {
            String s = l.getString();
            if (!s.isBlank()) loreLines.add(s);
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
            String line = Enchantment.getFullname(e.getKey(), e.getIntValue()).getString();
            if (!loreLines.contains(line)) continue;
            int first = -1;
            for (int i = 1; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(line)) { first = i; break; }
            }
            if (first < 0) continue;
            for (int i = first + 1; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(line)) { tooltip.remove(first); break; }
            }
        }
    }

    private static List<String[]> rules(TeslaMapsConfig c) {
        if (!c.enchantRenames.equals(rulesFrom)) {
            List<String[]> out = new ArrayList<>();
            for (String s : c.enchantRenames) {
                if (s == null) continue;
                int eq = s.indexOf('=');
                if (eq <= 0) continue;
                String from = s.substring(0, eq).trim(), to = s.substring(eq + 1).trim();
                if (!from.isEmpty() && !from.equals(to)) out.add(new String[]{from, to});
            }
            rules = out;
            rulesFrom = new ArrayList<>(c.enchantRenames);
        }
        return rules;
    }

    private static void rename(List<Component> lines, TeslaMapsConfig c) {
        List<String[]> r = rules(c);
        if (r.isEmpty()) return;
        for (int i = 1; i < lines.size(); i++) {
            Component line = lines.get(i);
            String text = line.getString();
            boolean hit = false;
            for (String[] rule : r) if (text.contains(rule[0])) { hit = true; break; }
            if (!hit) continue;
            MutableComponent out = Component.empty();
            boolean[] changed = {false};
            line.visit((FormattedText.StyledContentConsumer<Object>) (style, part) -> {
                String p = part;
                for (String[] rule : r) {
                    String n = replace(p, rule[0], rule[1]);
                    if (n != p) { p = n; changed[0] = true; }
                }
                out.append(Component.literal(p).withStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            if (changed[0]) lines.set(i, out);
        }
    }

    /** Replaces whole-word hits followed by a space (the level) or the end of the part. Same instance if none. */
    private static String replace(String s, String from, String to) {
        int idx = s.indexOf(from);
        if (idx < 0) return s;
        StringBuilder sb = null;
        int last = 0;
        while (idx >= 0) {
            int end = idx + from.length();
            boolean startOk = idx == 0 || !Character.isLetter(s.charAt(idx - 1));
            boolean endOk = end == s.length() || s.charAt(end) == ' ' || s.charAt(end) == ',';
            if (startOk && endOk) {
                if (sb == null) sb = new StringBuilder(s.length() + 16);
                sb.append(s, last, idx).append(to);
                last = end;
            }
            idx = s.indexOf(from, end);
        }
        if (sb == null) return s;
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private static void openEditor() {
        TeslaMapsConfig c = cfg();
        while (c.enchantRenames.size() < RENAME_ROWS) c.enchantRenames.add("");
        List<TextFieldsScreen.Field> fields = new ArrayList<>();
        for (int i = 0; i < RENAME_ROWS; i++) {
            int idx = i;
            fields.add(new TextFieldsScreen.Field("Rename " + (i + 1),
                    () -> cfg().enchantRenames.get(idx), v -> cfg().enchantRenames.set(idx, v)));
        }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new TextFieldsScreen(mc.screen, "Enchant Names", "Old=New, e.g. Ultimate Wise=UW", fields));
    }
}
