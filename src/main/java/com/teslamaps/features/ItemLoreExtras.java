package com.teslamaps.features;

// Ported from SkyHanni's PocketSackInASackDisplay.kt, SkyOcean's MidasPaidBreakdownModifier.kt +
// ArmadilloBlocksWalkedModifier.kt, NEU's petExtendExp; radioactive bonus after Skytils' ItemFeatures
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.pets.PetConstants;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemLoreExtras {

    private static final int MAX_STITCHES = 3;
    private static final Map<CustomData, String> STITCH = new IdentityHashMap<>();

    private static Feature feature;
    private static int ticks;
    private static int strength = -1;

    private static ItemStack petStack;
    private static Component petLine;

    private ItemLoreExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ItemLoreExtras")
                .when(() -> {
                    TeslaMapsConfig c = cfg();
                    return c.sackStitchOverlay || c.sackStitchLore || c.midasBidBreakdown || c.petFullXp
                            || c.prehistoricEggBlocks || c.radioactiveBonus;
                })
                .tick(ItemLoreExtras::tick)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Item Lore Extras");
                    b.toggle("Sack Stitches On Icon", () -> c.sackStitchOverlay, v -> c.sackStitchOverlay = v)
                            .desc("Green number on a sack's icon: how many Pocket Sack-in-a-Sacks are applied.");
                    b.toggle("  Stitched n/3 Lore Line", () -> c.sackStitchLore, v -> c.sackStitchLore = v)
                            .desc("Replaces the sack's lore line with \"stitched 2/3 times\", green at 3/3.");
                    b.toggle("Midas Bid Breakdown", () -> c.midasBidBreakdown, v -> c.midasBidBreakdown = v)
                            .desc("Original Bid and Added Coins under \"Price paid:\" on Midas weapons.");
                    b.toggle("Full Pet XP Numbers", () -> c.petFullXp, v -> c.petFullXp = v)
                            .desc("Pet tooltips get an \"EXP: 2,345/10,000\" line instead of only the rounded bar numbers.");
                    b.toggle("Prehistoric Egg Blocks Walked", () -> c.prehistoricEggBlocks, v -> c.prehistoricEggBlocks = v)
                            .desc("\"Blocks Walked: n\" on the Prehistoric Egg.");
                    b.toggle("Tarantula Helmet Radioactive Bonus", () -> c.radioactiveBonus, v -> c.radioactiveBonus = v)
                            .desc("Crit Damage bonus from your Strength (tab list Stats widget) under the helmet's Crit Damage line.");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!stack.isEmpty() && lines.size() > 1 && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        if (Minecraft.getInstance().screen == null && STITCH.size() > 0) STITCH.clear();
        if (!cfg().radioactiveBonus) return;
        int found = -1;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!line.startsWith("Strength:")) continue;
            StringBuilder digits = new StringBuilder();
            for (int i = 9; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (Character.isDigit(ch)) digits.append(ch);
                else if (ch != ',' && !digits.isEmpty()) break;
            }
            if (!digits.isEmpty()) found = Integer.parseInt(digits.toString());
            break;
        }
        strength = found;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    private static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        if (c.sackStitchLore && id.endsWith("_SACK")) sackLore(stack, lines);
        if (c.midasBidBreakdown) midas(stack, lines);
        if (c.petFullXp && id.equals("PET")) petXp(stack, lines);
        if (c.prehistoricEggBlocks && id.equals("PREHISTORIC_EGG")) egg(stack, lines);
        if (c.radioactiveBonus && id.equals("TARANTULA_HELMET")) radioactive(lines);
    }

    private static void sackLore(ItemStack stack, List<Component> lines) {
        CompoundTag t = tag(stack);
        if (t == null || !t.contains("sack_pss")) return;
        int applied = t.getIntOr("sack_pss", 0);
        String col = applied >= MAX_STITCHES ? "§a" : "§b";
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).getString().contains("This sack is")) continue;
            lines.set(i, Component.literal("§7This sack is stitched " + col + applied + "§7/" + col + MAX_STITCHES));
            if (i + 1 < lines.size()) lines.set(i + 1, Component.literal("§7times with a §cPocket Sack-in-a-Sack§7."));
            return;
        }
    }

    private static void midas(ItemStack stack, List<Component> lines) {
        CompoundTag t = tag(stack);
        if (t == null || !t.contains("additional_coins") || !t.contains("winning_bid")) return;
        long bid = t.getLongOr("winning_bid", 0);
        long added = t.getLongOr("additional_coins", 0);
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).getString().contains("Price paid: ")) continue;
            lines.add(i + 1, Component.literal("§8Original Bid: §6" + fmt(bid)));
            lines.add(i + 2, Component.literal("§8Added Coins: §6" + fmt(added)));
            return;
        }
    }

    private static void petXp(ItemStack stack, List<Component> lines) {
        int progress = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String s = StringUtil.stripTrim(lines.get(i).getString());
            if (s.equals("MAX LEVEL")) return;
            if (s.startsWith("Progress to Level")) { progress = i; break; }
        }
        if (progress < 0 || progress + 1 >= lines.size()) return;
        if (stack != petStack) {
            petStack = stack;
            petLine = buildPetLine(stack);
        }
        if (petLine != null) lines.add(progress + 2, petLine);
    }

    private static Component buildPetLine(ItemStack stack) {
        try {
            CompoundTag t = tag(stack);
            if (t == null) return null;
            String info = t.getStringOr("petInfo", "");
            if (info.isEmpty()) return null;
            JsonObject o = JsonParser.parseString(info).getAsJsonObject();
            if (!o.has("type") || !o.has("exp")) return null;
            String type = o.get("type").getAsString();
            String tier = o.has("tier") ? o.get("tier").getAsString() : "COMMON";
            double exp = o.get("exp").getAsDouble();
            int level = PetConstants.xpToLevel(type, tier, exp);
            double need = PetConstants.costForLevel(type, tier, level);
            if (need <= 0) return null;
            double cur = exp - PetConstants.levelToXp(type, tier, level);
            return Component.literal("§7EXP: §e" + fmt(Math.round(cur)) + "§6/§e" + fmt(Math.round(need)));
        } catch (Exception e) {
            return null;
        }
    }

    private static void egg(ItemStack stack, List<Component> lines) {
        CompoundTag t = tag(stack);
        int walked = t == null ? 0 : t.getIntOr("blocks_walked", 0);
        int at = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).getString().isBlank()) { at = i; break; }
        }
        Component line = Component.literal("§7Blocks Walked: §a" + fmt(walked));
        if (at < 0) {
            lines.add(line);
        } else {
            lines.add(at + 1, line);
            lines.add(at + 2, Component.empty());
        }
    }

    private static void radioactive(List<Component> lines) {
        if (strength < 0) return;
        int bonus = Math.min(strength, 1000) / 10;
        for (int i = 0; i < lines.size(); i++) {
            if (!StringUtil.stripTrim(lines.get(i).getString()).startsWith("Crit Damage:")) continue;
            lines.add(i + 1, Component.literal("§8Radioactive Bonus: §c+" + bonus + "%"));
            return;
        }
    }

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (!cfg().sackStitchOverlay || !feature.active()) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() != 1) return;
        if (!ItemUtil.skyblockId(stack).endsWith("_SACK")) return;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        String text = STITCH.get(d);
        if (text == null) {
            if (STITCH.size() > 512) STITCH.clear();
            CompoundTag t = d.copyTag();
            text = t.contains("sack_pss") ? String.valueOf(t.getIntOr("sack_pss", 0)) : "";
            STITCH.put(d, text);
        }
        if (text.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        ctx.pose().pushMatrix();
        ctx.pose().translate(slot.x + 13, slot.y + 1);
        ctx.pose().scale(0.9f, 0.9f);
        ctx.text(font, text, 0, 0, 0xFF55FF55);
        ctx.pose().popMatrix();
    }
}
