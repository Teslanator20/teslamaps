package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Writes which enchant a book holds onto its icon ("Leg I", "Chim II"), like NEU/Skyblocker. */
public class EnchantAbbrev {

    private static final int COLOR_NORMAL = 0x5555FF;
    private static final int COLOR_ULTIMATE = 0xFF55FF;

    private static final Map<String, String> ULTIMATE = new HashMap<>();
    private static final Map<String, String> NORMAL = new HashMap<>();

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
    }

    private record Text(Component roman, Component numbers) {}
    private static final Text NONE = new Text(null, null);

    // keyed by the CustomData object, which is stable per stack - never hash whole nbt per frame
    private static final Map<CustomData, Text> cache = new IdentityHashMap<>();

    public static Component get(ItemStack stack) {
        if (stack == null || !stack.is(Items.ENCHANTED_BOOK)) return null;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.enchantAbbrev) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        if (cache.size() > 2048) cache.clear();
        Text text = cache.computeIfAbsent(data, EnchantAbbrev::build);
        return cfg.enchantLevelsAsNumbers ? text.numbers : text.roman;
    }

    private static Text build(CustomData data) {
        CompoundTag enchants = data.copyTag().getCompoundOrEmpty("enchantments");
        if (enchants.size() != 1) return NONE;
        String id = enchants.keySet().iterator().next();
        int level = enchants.getIntOr(id, 0);
        if (level <= 0) return NONE;

        String name = ULTIMATE.get(id);
        boolean ultimate = name != null;
        if (!ultimate) name = NORMAL.get(id);
        if (name == null) name = fallback(id);

        TextColor color = TextColor.fromRgb(ultimate ? COLOR_ULTIMATE : COLOR_NORMAL);
        return new Text(
                Component.literal(name + " " + roman(level)).withStyle(s -> s.withColor(color)),
                Component.literal(name + " " + level).withStyle(s -> s.withColor(color)));
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

    /** Over a 16px item at (itemX, itemY), scaled down until the whole label fits. */
    public static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int itemX, int itemY) {
        Component text = get(stack);
        if (text == null) return;
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(1f, 16f / font.width(text));
        ctx.pose().pushMatrix();
        ctx.pose().translate(itemX, itemY);
        ctx.pose().scale(scale, scale);
        ctx.text(font, text, 0, 0, 0xFFFFFFFF);
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
