package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Writes which enchant a book holds onto its icon ("Leg I", "Chim II"), like NEU/Skyblocker. */
public class EnchantAbbrev {

    private static final Map<String, String> ULTIMATE = new HashMap<>();
    private static final Map<String, String> NORMAL = new HashMap<>();
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        ULTIMATE.put("ultimate_bank", "Bank");
        ULTIMATE.put("ultimate_bobbin_time", "BT");
        ULTIMATE.put("ultimate_chimera", "Chim");
        ULTIMATE.put("ultimate_combo", "Combo");
        ULTIMATE.put("ultimate_crop_fever", "CF");
        ULTIMATE.put("ultimate_reiterate", "Dup");
        ULTIMATE.put("ultimate_fatal_tempo", "FT");
        ULTIMATE.put("ultimate_first_impression", "FI");
        ULTIMATE.put("ultimate_flash", "Flash");
        ULTIMATE.put("ultimate_flowstate", "Flow");
        ULTIMATE.put("ultimate_habanero_tactics", "Hab");
        ULTIMATE.put("ultimate_inferno", "Inf");
        ULTIMATE.put("ultimate_last_stand", "LS");
        ULTIMATE.put("ultimate_legion", "Leg");
        ULTIMATE.put("ultimate_missile", "Msl");
        ULTIMATE.put("ultimate_no_pain_no_gain", "NPNG");
        ULTIMATE.put("ultimate_one_for_all", "OFA");
        ULTIMATE.put("ultimate_refrigerate", "Refr");
        ULTIMATE.put("ultimate_rend", "Rend");
        ULTIMATE.put("ultimate_soul_eater", "SE");
        ULTIMATE.put("ultimate_sunset", "Sun");
        ULTIMATE.put("ultimate_swarm", "Swarm");
        ULTIMATE.put("ultimate_the_one", "TO");
        ULTIMATE.put("ultimate_jerry", "Jerry");
        ULTIMATE.put("ultimate_wise", "Wise");
        ULTIMATE.put("ultimate_wisdom", "Wisd");

        NORMAL.put("sharpness", "Sharp");
        NORMAL.put("critical", "Crit");
        NORMAL.put("efficiency", "Eff");
        NORMAL.put("protection", "Prot");
        NORMAL.put("growth", "Grow");
        NORMAL.put("power", "Power");
        NORMAL.put("compact", "Comp");
        NORMAL.put("expertise", "Pert");
        NORMAL.put("experience", "XP");
        NORMAL.put("hecatomb", "Hec");
        NORMAL.put("champion", "Champ");
        NORMAL.put("cultivating", "Cult");
        NORMAL.put("dedication", "Ded");
        NORMAL.put("scavenger", "Scav");
        NORMAL.put("looting", "Loot");
        NORMAL.put("overload", "Over");
        NORMAL.put("smite", "Smite");
        NORMAL.put("vicious", "Vic");
        NORMAL.put("lethality", "Leth");
        NORMAL.put("syphon", "Syph");
        NORMAL.put("vampirism", "Vamp");
        NORMAL.put("thunderlord", "TL");
        NORMAL.put("thunderbolt", "TB");
        NORMAL.put("cubism", "Cube");
        NORMAL.put("execute", "Exec");
        NORMAL.put("prosecute", "Prosc");
        NORMAL.put("cleave", "Clv");
        NORMAL.put("venomous", "Venom");
        NORMAL.put("tabasco", "Tab");
        NORMAL.put("cayenne", "Cay");
        NORMAL.put("pesterminator", "Pest");
        NORMAL.put("harvesting", "Harv");
        NORMAL.put("replenish", "Repl");
        NORMAL.put("pristine", "Prist");
        NORMAL.put("fortune", "Fort");
        NORMAL.put("lapidary", "Lapi");
        NORMAL.put("paleontologist", "Paleo");
        NORMAL.put("piscary", "Pisc");
        NORMAL.put("angler", "Angl");
        NORMAL.put("caster", "Cast");
        NORMAL.put("frail", "Frail");
        NORMAL.put("magnet", "Magn");
        NORMAL.put("lure", "Lure");
        NORMAL.put("spiked_hook", "Spike");
        NORMAL.put("charm", "Charm");
        NORMAL.put("corruption", "Corr");
        NORMAL.put("chance", "Chnc");
        NORMAL.put("snipe", "Snipe");
        NORMAL.put("overkill", "OK");
        NORMAL.put("rejuvenate", "Rejuv");
        NORMAL.put("respite", "Resp");
        NORMAL.put("smoldering", "Smold");
        NORMAL.put("thorns", "Thorn");
        NORMAL.put("reflection", "Refl");
        NORMAL.put("transylvanian", "Trans");
        NORMAL.put("stealth", "Stlth");
        NORMAL.put("quantum", "Quant");
        NORMAL.put("divine_gift", "DG");
        NORMAL.put("great_spook", "Spook");
        NORMAL.put("scuba", "Scuba");
        NORMAL.put("toxophilite", "Toxo");
        NORMAL.put("absorb", "Absb");
        NORMAL.put("aqua_affinity", "AA");
        NORMAL.put("arcane", "Wood");
        NORMAL.put("aiming", "DT");
        NORMAL.put("bane_of_arthropods", "BoA");
        NORMAL.put("big_brain", "BB");
        NORMAL.put("blast_protection", "BP");
        NORMAL.put("blessing", "Bless");
        NORMAL.put("bug_blender", "Bug");
        NORMAL.put("counter_strike", "CS");
        NORMAL.put("delicate", "Deli");
        NORMAL.put("depth_strider", "DS");
        NORMAL.put("dragon_hunter", "Grav");
        NORMAL.put("ender_slayer", "ES");
        NORMAL.put("feast", "Feast");
        NORMAL.put("feather_falling", "FF");
        NORMAL.put("ferocious_mana", "Viv");
        NORMAL.put("fire_aspect", "FA");
        NORMAL.put("fire_protection", "FP");
        NORMAL.put("first_strike", "FS");
        NORMAL.put("flame", "Flame");
        NORMAL.put("forest_pledge", "FPl");
        NORMAL.put("giant_killer", "GK");
        NORMAL.put("green_thumb", "GT");
        NORMAL.put("hardened_mana", "HV");
        NORMAL.put("ice_cold", "Ice");
        NORMAL.put("impaling", "Imp");
        NORMAL.put("infinite_quiver", "IQ");
        NORMAL.put("karma", "Karma");
        NORMAL.put("knockback", "KB");
        NORMAL.put("life_steal", "LS");
        NORMAL.put("luck", "Luck");
        NORMAL.put("luck_of_the_sea", "LotS");
        NORMAL.put("magmarizer", "Pyro");
        NORMAL.put("mana_steal", "MS");
        NORMAL.put("mana_vampire", "VV");
        NORMAL.put("petalfall", "Petal");
        NORMAL.put("piercing", "Pierc");
        NORMAL.put("projectile_protection", "PP");
        NORMAL.put("prosperity", "Prosp");
        NORMAL.put("punch", "Punch");
        NORMAL.put("quick_bite", "QB");
        NORMAL.put("rainbow", "Rainb");
        NORMAL.put("respiration", "Respi");
        NORMAL.put("silk_touch", "Silk");
        NORMAL.put("small_brain", "SB");
        NORMAL.put("smarty_pants", "SP");
        NORMAL.put("smelting_touch", "Smelt");
        NORMAL.put("strong_mana", "SV");
        NORMAL.put("sugar_rush", "Sugar");
        NORMAL.put("tidal", "Tidal");
        NORMAL.put("titan_killer", "TK");
        NORMAL.put("triple_strike", "TS");
        NORMAL.put("true_protection", "TP");
        NORMAL.put("turbo_cactus", "TCac");
        NORMAL.put("turbo_cane", "TCan");
        NORMAL.put("turbo_carrot", "TCar");
        NORMAL.put("turbo_coco", "TCoc");
        NORMAL.put("turbo_melon", "TMel");
        NORMAL.put("turbo_moonflower", "TMoo");
        NORMAL.put("turbo_mushrooms", "TMus");
        NORMAL.put("turbo_potato", "TPot");
        NORMAL.put("turbo_pumpkin", "TPum");
        NORMAL.put("turbo_rose", "TRos");
        NORMAL.put("turbo_sunflower", "TSun");
        NORMAL.put("turbo_warts", "TWar");
        NORMAL.put("turbo_wheat", "TWhe");

        ALIAS.put("dragon_tracer", "aiming");
        ALIAS.put("woodsplitter", "arcane");
        ALIAS.put("gravity", "dragon_hunter");
        ALIAS.put("vivacious_vitality", "ferocious_mana");
        ALIAS.put("hardened_vitality", "hardened_mana");
        ALIAS.put("pyroclasm", "magmarizer");
        ALIAS.put("vampiric_vitality", "mana_vampire");
        ALIAS.put("strong_vitality", "strong_mana");
        ALIAS.put("prismatic", "pristine");
        ALIAS.put("drain", "syphon");
        ALIAS.put("duplex", "ultimate_reiterate");
        ALIAS.put("turbo_cacti", "turbo_cactus");
        ALIAS.put("turbo_mushroom", "turbo_mushrooms");
        ALIAS.put("turbo_wart", "turbo_warts");
    }

    private record Text(Component name, String roman, String numbers, boolean ultimate) {}
    private static final Text NONE = new Text(null, null, null, false);

    // keyed by the CustomData object, which is stable per stack - never hash whole nbt per frame
    private static final Map<CustomData, Text> cache = new IdentityHashMap<>();

    // bazaar books carry no enchant nbt, only "Sharpness VII" as the name
    private static final Map<ItemStack, Text> byName = new WeakHashMap<>();

    private static Text get(ItemStack stack) {
        if (stack == null || !stack.is(Items.ENCHANTED_BOOK)) return null;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.enchantAbbrev || !SlotText.shown(SlotText.ENCHANT)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        Text text = NONE;
        if (data != null) {
            if (cache.size() > 2048) cache.clear();
            text = cache.computeIfAbsent(data, EnchantAbbrev::build);
        }
        if (text.name == null && c.enchantAbbrevBazaar) {
            text = byName.get(stack);
            if (text == null) {
                text = fromName(stack);
                if (byName.size() > 1024) byName.clear();
                byName.put(stack, text);
            }
        }
        return text.name == null ? null : text;
    }

    private static Text build(CustomData data) {
        CompoundTag enchants = data.copyTag().getCompoundOrEmpty("enchantments");
        if (enchants.size() != 1) return NONE;
        String id = enchants.keySet().iterator().next();
        return make(id, enchants.getIntOr(id, 0), true);
    }

    private static Text fromName(ItemStack stack) {
        String name = com.teslamaps.utils.StringUtil.stripTrim(stack.getHoverName().getString());
        int sp = name.lastIndexOf(' ');
        if (sp <= 0) return NONE;
        int level = romanValue(name.substring(sp + 1));
        if (level <= 0) return NONE;
        String id = name.substring(0, sp).toLowerCase(java.util.Locale.ROOT)
                .replace("'", "").replace('-', '_').replace(' ', '_');
        id = ALIAS.getOrDefault(id, id);
        if (!ULTIMATE.containsKey(id) && !NORMAL.containsKey(id)) {
            String ult = "ultimate_" + id;
            if (!ULTIMATE.containsKey(ult)) return NONE;
            id = ult;
        }
        return make(id, level, false);
    }

    private static Text make(String id, int level, boolean allowFallback) {
        if (level <= 0) return NONE;
        String name = ULTIMATE.get(id);
        boolean ultimate = name != null;
        if (!ultimate) name = NORMAL.get(id);
        if (name == null) {
            if (!allowFallback) return NONE;
            name = fallback(id);
        }
        return new Text(Component.literal(name).withStyle(s -> s.withBold(true)),
                roman(level), String.valueOf(level), ultimate);
    }

    private static int romanValue(String s) {
        if (s.isEmpty()) return -1;
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                default -> -1;
            };
            if (v < 0) return -1;
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }

    /** Unknown enchant: initials for multi-word ids (bane_of_arthropods = BOA), else first 4 letters. */
    private static String fallback(String id) {
        if (id.indexOf('_') >= 0) {
            StringBuilder sb = new StringBuilder();
            for (String word : id.split("_")) {
                if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0)));
            }
            return sb.toString();
        }
        String cut = id.length() > 4 ? id.substring(0, 4) : id;
        return Character.toUpperCase(cut.charAt(0)) + cut.substring(1);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static String roman(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }

    /** Name bold across the top of a 16px item, level small in the bottom right. */
    public static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int itemX, int itemY) {
        Text text = get(stack);
        if (text == null) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        Font font = Minecraft.getInstance().font;

        int nameColor = TeslaMapsConfig.parseColor(
                text.ultimate ? cfg.colorEnchantAbbrevUltimate : cfg.colorEnchantAbbrev);
        String level = cfg.enchantLevelsAsNumbers ? text.numbers : text.roman;

        float nameScale = Math.min(1f, 17f / font.width(text.name));
        float levelScale = Math.min(0.75f, 10f / font.width(level));

        ctx.pose().pushMatrix();
        ctx.pose().translate(itemX, itemY);

        ctx.pose().pushMatrix();
        ctx.pose().scale(nameScale, nameScale);
        ctx.text(font, text.name, 0, 0, nameColor);
        ctx.pose().popMatrix();

        ctx.pose().translate(17f - font.width(level) * levelScale, 17f - 8f * levelScale);
        ctx.pose().scale(levelScale, levelScale);
        ctx.text(font, level, 0, 0, TeslaMapsConfig.parseColor(cfg.colorEnchantAbbrevLevel));
        ctx.pose().popMatrix();
    }

    /** Over an ItemGrid slot of the given size at (sx, sy). */
    public static void drawScaled(GuiGraphicsExtractor ctx, ItemStack stack, int sx, int sy, int size) {
        if (get(stack) == null) return;
        float cell = (size - 2) / 16f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(sx + 1, sy + 1);
        ctx.pose().scale(cell, cell);
        draw(ctx, stack, 0, 0);
        ctx.pose().popMatrix();
    }
}
