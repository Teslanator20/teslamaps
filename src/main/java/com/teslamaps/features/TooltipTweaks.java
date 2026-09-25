package com.teslamaps.features;

// Gear score: Devonian's RemoveGearScore.kt. Drill lore: SkyOcean's DrillLoreModifier.kt.
// Evolving items: Skyblocker's EvolvingItemProgressTooltip + EvolvingItemAdder.
// Missing enchants: NEU's ItemTooltipListener. Gemstones: Skytils' showGemstones.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbRepo;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipTweaks {

    private static final String[] RARITIES = {"VERY SPECIAL", "UNCOMMON", "COMMON", "RARE", "EPIC", "LEGENDARY",
            "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE", "ADMIN"};
    private static final Pattern NUMBER = Pattern.compile("\\+?([\\d.]+)");

    private static final class Extra {
        Component age;
        List<Component> gems;
        List<Component> missing;
        String rarityLine;
        boolean missingDone;
    }

    private static final Map<ItemStack, Extra> CACHE = new WeakHashMap<>();
    private static final Map<ItemStack, String> BONUS = new WeakHashMap<>();
    private static Feature feature;

    private TooltipTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.missingEnchants || c.removeGearScore || c.drillLoreCompact || c.evolvingProgress || c.gemstoneList;
    }

    public static void register() {
        feature = Feature.of("TooltipTweaks").when(TooltipTweaks::any)
                .config("Tooltips & Value", b -> {
                    b.label("Tooltip Extras");
                    b.toggle("Missing Enchants (Shift)", () -> cfg().missingEnchants, v -> cfg().missingEnchants = v)
                            .desc("Hold Shift to list the enchants this item type can still get (NEU repo).");
                    b.toggle("Compact Drill Parts", () -> cfg().drillLoreCompact, v -> cfg().drillLoreCompact = v)
                            .desc("Fuel Tank / Engine / Upgrade Module as three short lines, red when not installed.");
                    b.toggle("Evolving Item Age", () -> cfg().evolvingProgress, v -> cfg().evolvingProgress = v)
                            .desc("Truffle, Moby Duck, Rosewater Flask, Jyrre, ... show their held time out of the max.");
                    b.toggle("  Bonus In Slot", () -> cfg().evolvingSlotText, v -> cfg().evolvingSlotText = v);
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!feature.active() || stack.isEmpty() || lines.isEmpty()) return;
            try {
                onTooltip(stack, lines);
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.debug("[TooltipTweaks] {}", e.toString());
            }
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;

        if (c.removeGearScore) {
            lines.removeIf(l -> StringUtil.strip(l.getString()).startsWith("Gear Score: "));
        }
        if (c.drillLoreCompact && id.contains("DRILL")) compactDrill(stack, lines);

        Extra extra = CACHE.get(stack);
        if (extra == null) {
            if (CACHE.size() > 512) CACHE.clear();
            extra = build(stack, id);
            CACHE.put(stack, extra);
        }

        if (c.evolvingProgress && extra.age != null) lines.add(extra.age);
        if (c.gemstoneList && !extra.gems.isEmpty()) lines.addAll(extra.gems);
        if (c.missingEnchants && Minecraft.getInstance().hasShiftDown()) {
            if (!extra.missingDone) missing(stack, extra);
            if (extra.missing != null && !extra.missing.isEmpty()) insertBeforeRarity(lines, extra.rarityLine, extra.missing);
        }
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    private static Extra build(ItemStack stack, String id) {
        Extra e = new Extra();
        CompoundTag ea = data(stack);
        e.age = age(id, ea);
        List<Component> gems = new ArrayList<>();
        List<String[]> applied = EstimatedValue.appliedGems(ea, true);
        if (!applied.isEmpty()) {
            gems.add(Component.literal("§bGemstones:"));
            for (String[] g : applied) {
                gems.add(Component.literal(" §7- §" + EstimatedValue.gemColor(g[1])
                        + StringUtil.titleCase(g[0]) + " " + StringUtil.titleCase(g[1])));
            }
        }
        e.gems = gems;
        return e;
    }

    // ---------------------------------------------------------------- evolving items

    private static Component age(String id, CompoundTag ea) {
        int held;
        switch (id) {
            case "DISCRITE" -> held = ea.getIntOr("rift_discrite_seconds", 0);
            case "NEW_BOTTLE_OF_JYRRE" -> held = ea.getIntOr("bottle_of_jyrre_seconds", 0);
            case "TRAINING_WEIGHTS" -> held = ea.getIntOr("trainingWeightsHeldTime", 0);
            case "DARK_CACAO_TRUFFLE", "MOBY_DUCK", "ROSEWATER_FLASK", "NOT_VERY_MOLDY_BREAD" ->
                    held = ea.getIntOr("seconds_held", 0);
            default -> {
                return null;
            }
        }
        int max = id.equals("TRAINING_WEIGHTS") ? 5500 * 60 : 3600 * 300;
        return Component.literal(String.format(Locale.ROOT, "§7Age: §c%.1f§7/§c%.1fh §7(§c%.1f%%§7)",
                held / 3600f, max / 3600f, 100.0 * held / max));
    }

    private static String bonusPrefix(String id) {
        return switch (id) {
            case "NEW_BOTTLE_OF_JYRRE", "DARK_CACAO_TRUFFLE", "DISCRITE", "MOBY_DUCK", "ROSEWATER_FLASK",
                 "NOT_VERY_MOLDY_BREAD" -> "Current Bonus: ";
            case "TRAINING_WEIGHTS" -> "Strength Gain: ";
            case "BOTTLE_OF_JYRRE" -> "Intelligence Bonus: ";
            default -> null;
        };
    }

    private static String bonus(ItemStack stack, String prefix) {
        String cached = BONUS.get(stack);
        if (cached != null) return cached;
        String out = "";
        for (String line : ItemUtil.loreLines(stack)) {
            String t = line.trim();
            if (!t.startsWith(prefix)) continue;
            Matcher m = NUMBER.matcher(t.substring(prefix.length()));
            if (m.find()) {
                String n = m.group(1);
                if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
                out = n;
            }
            break;
        }
        if (BONUS.size() > 512) BONUS.clear();
        BONUS.put(stack, out);
        return out;
    }

    /** Current bonus in the bottom-left corner of an evolving item. Slot-relative coordinates. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.evolvingProgress || !c.evolvingSlotText || !SlotText.shown(SlotText.EVOLVING)) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        String prefix = bonusPrefix(ItemUtil.skyblockId(stack));
        if (prefix == null) return;
        String text = bonus(stack, prefix);
        if (text.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(1f, 16f / Math.max(1, font.width(text)));
        ctx.pose().pushMatrix();
        ctx.pose().translate(slot.x, slot.y + 17 - 8 * scale);
        ctx.pose().scale(scale, scale);
        ctx.text(font, text, 0, 0, 0xFF55FF55);
        ctx.pose().popMatrix();
    }

    // ---------------------------------------------------------------- drills

    private static void compactDrill(ItemStack stack, List<Component> lines) {
        int start = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (StringUtil.strip(lines.get(i).getString()).contains("Tank")) {
                start = i;
                break;
            }
        }
        if (start < 0) return;
        int end = start;
        for (int block = 0; block < 3; block++) {
            int blank = -1;
            for (int j = end; j < lines.size(); j++) {
                if (StringUtil.stripTrim(lines.get(j).getString()).isEmpty()) {
                    blank = j;
                    break;
                }
            }
            if (blank < 0) return;
            end = blank + 1;
        }
        CompoundTag ea = data(stack);
        List<Component> repl = List.of(
                part("Fuel Tank: ", ea, "drill_part_fuel_tank"),
                part("Drill Engine: ", ea, "drill_part_engine"),
                part("Upgrade Module: ", ea, "drill_part_upgrade_module"),
                Component.empty());
        lines.subList(start, end).clear();
        lines.addAll(start, repl);
    }

    private static Component part(String label, CompoundTag ea, String key) {
        String id = ea.getStringOr(key, "");
        if (id.isBlank()) return Component.literal("§7" + label + "§cNot Installed");
        id = id.toUpperCase(Locale.ROOT);
        String name = SkyblockItems.coloredName(id);
        if (name == null) name = "§a" + StringUtil.titleCase(id.replace('_', ' '));
        return Component.literal("§7" + label + name);
    }

    // ---------------------------------------------------------------- missing enchants

    private static void missing(ItemStack stack, Extra extra) {
        SbRepo.load();
        if (!SbRepo.loaded()) return;
        extra.missingDone = true;
        CompoundTag enchants = data(stack).getCompoundOrEmpty("enchantments");
        if (enchants.isEmpty()) return;
        Set<String> have = enchants.keySet();

        List<String> lore = ItemUtil.loreLines(stack);
        List<String> pool = null;
        for (int i = lore.size() - 1; i >= 0 && pool == null; i--) {
            String type = itemType(lore.get(i));
            if (type == null) continue;
            pool = SbRepo.enchantsForType(type);
            if (pool != null) extra.rarityLine = lore.get(i).trim();
        }
        if (pool == null) return;

        Set<String> ignore = new java.util.HashSet<>();
        for (Set<String> group : SbRepo.enchantPools()) {
            for (String h : have) {
                if (group.contains(h)) {
                    ignore.addAll(group);
                    break;
                }
            }
        }
        List<String> names = new ArrayList<>();
        for (String id : pool) {
            if (id.startsWith("ultimate_") || ignore.contains(id) || have.contains(id)) continue;
            names.add(SbRepo.enchantDisplayName(id));
        }
        if (names.isEmpty()) {
            extra.missing = List.of();
            return;
        }
        List<Component> out = new ArrayList<>();
        out.add(Component.empty());
        StringBuilder line = new StringBuilder("§cMissing: §7");
        int len = 9;
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (i > 0 && len + n.length() > 40) {
                line.append(',');
                out.add(Component.literal(line.toString()));
                line = new StringBuilder("§7");
                len = 0;
            } else if (i > 0) {
                line.append(", ");
                len += 2;
            }
            line.append(n);
            len += n.length();
        }
        out.add(Component.literal(line.toString()));
        extra.missing = out;
    }

    /** "LEGENDARY DUNGEON SWORD" -> "SWORD", null when the line is no rarity line. */
    private static String itemType(String loreLine) {
        String t = loreLine.trim();
        if (t.startsWith("a ") && t.endsWith(" a")) t = t.substring(2, t.length() - 2).trim();
        if (t.startsWith("SHINY ")) t = t.substring(6);
        for (String r : RARITIES) {
            if (!t.startsWith(r + " ")) continue;
            String rest = t.substring(r.length() + 1).trim();
            if (rest.startsWith("DUNGEON ")) rest = rest.substring(8).trim();
            return rest.isEmpty() ? null : rest;
        }
        return null;
    }

    private static void insertBeforeRarity(List<Component> lines, String rarity, List<Component> add) {
        if (rarity != null) {
            for (int i = lines.size() - 1; i > 0; i--) {
                if (StringUtil.stripTrim(lines.get(i).getString()).equals(rarity)) {
                    lines.addAll(i, add);
                    return;
                }
            }
        }
        lines.addAll(add);
    }
}
