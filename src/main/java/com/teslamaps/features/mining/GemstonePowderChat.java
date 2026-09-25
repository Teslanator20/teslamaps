package com.teslamaps.features.mining;

// After SkyHanni's ActualGemstonePowderDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Treasure chest "Gemstone Powder xN" line gets the real amount and a hover with every multiplier. */
public final class GemstonePowderChat {

    private static final Pattern LINE = Pattern.compile("^ {4}Gemstone Powder(?: x([\\d,]+))?$");
    private static final Pattern DRILL_BASE = Pattern.compile("Grants \\+(\\d+)% Gemstone Powder");
    private static final Pattern DRILL_UPGRADE = Pattern.compile("Earn \\+(\\d+)% Powder from all sources");
    private static final long SKY_MALL_MS = 20 * 60_000L;

    private static Feature feature;

    private GemstonePowderChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GemstonePowderChat").section("Mining").when(() -> cfg().effectivePowderChat)
                .serverChat(GemstonePowderChat::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Effective Gemstone Powder In Chat", () -> c.effectivePowderChat, v -> c.effectivePowderChat = v)
                            .desc("Treasure chest Gemstone Powder line shows the amount you really get; hover for the multipliers (HotM Powder Buff, drill, Sky Mall, Scatha). Open the HotM menu once so the Powder Buff level is known.")
                            .tags("powder", "gemstone", "multiplier", "chat", "crystal hollows");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("New buff: ")) return;
        TeslaMapsConfig c = cfg();
        c.skyMallPowderUntil = t.contains("Powder") ? System.currentTimeMillis() + SKY_MALL_MS : 0;
    }

    /** From ChatMixin. */
    public static Component rewrite(Component msg) {
        if (feature == null || !cfg().effectivePowderChat || !feature.active()) return msg;
        String raw = msg.getString();
        if (raw.indexOf("Gemstone Powder") < 0) return msg;
        Matcher m = LINE.matcher(StringUtil.strip(raw));
        if (!m.matches() || !SkyblockUtils.isInArea("Crystal Hollows")) return msg;
        long base = 1;
        if (m.group(1) != null) {
            try { base = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return msg; }
        }
        List<String> tree = new ArrayList<>();
        double total = 1;

        double hotm = 0;
        List<String> hotmLines = new ArrayList<>();
        int buff = cfg().hotmPowderBuffLevel;
        if (buff > 0) {
            hotm += buff / 100.0;
            hotmLines.add("§8  └ §8HotM Powder Buff: §2" + pct(buff / 100.0));
        }
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (!held.isEmpty() && ItemUtil.skyblockId(held).contains("DRILL")) {
            double drillBase = 0, upgrade = 0;
            for (String line : ItemUtil.loreLines(held)) {
                String l = StringUtil.strip(line);
                Matcher b = DRILL_BASE.matcher(l);
                if (b.find()) drillBase += Integer.parseInt(b.group(1)) / 100.0;
                Matcher u = DRILL_UPGRADE.matcher(l);
                if (u.find()) upgrade += Integer.parseInt(u.group(1)) / 100.0;
            }
            if (drillBase + upgrade > 0) {
                hotm += drillBase + upgrade;
                hotmLines.add("§8  └ §8" + StringUtil.strip(held.getHoverName().getString()) + ": §2" + pct(drillBase + upgrade));
                if (drillBase > 0) hotmLines.add("§5    └ §5Drill Base: §b" + pct(drillBase));
                if (upgrade > 0) hotmLines.add("§5    └ §5Goblin Egg: §b" + pct(upgrade));
            }
        }
        if (hotm > 0) {
            total *= 1 + hotm;
            tree.add("§7└ §7HotM Powder Buffs: §a" + mult(1 + hotm));
            tree.addAll(hotmLines);
        }
        if (cfg().skyMallPowderUntil > System.currentTimeMillis()) {
            total *= 1.15;
            tree.add("§7└ §7Sky Mall: §a" + mult(1.15));
        }
        if ("SCATHA".equals(PetTracker.internalName()) && "LEGENDARY".equalsIgnoreCase(PetTracker.rarity()) && PetTracker.level() > 0) {
            double pet = 0.2 * PetTracker.level() / 100.0;
            total *= 1 + pet;
            tree.add("§7└ §7[Lvl " + PetTracker.level() + "] §6Scatha: §a" + mult(1 + pet));
        }
        if (total <= 1.0) return msg;
        long actual = (long) (base * total);
        List<String> hover = new ArrayList<>();
        hover.add("§7Base Powder: §e" + String.format(Locale.ROOT, "%,d", base));
        hover.add("");
        hover.add("§7Total Multiplier: §6" + mult(total));
        hover.addAll(tree);
        hover.add("");
        hover.add("§7Actual Powder: §d" + String.format(Locale.ROOT, "%,d", actual));
        hover.add("§8Double powder events and attribute shards are not counted");
        Component tip = Component.literal(String.join("\n", hover));
        return Component.literal("    §dGemstone Powder §8x" + String.format(Locale.ROOT, "%,d", base)
                        + " §7(x" + String.format(Locale.ROOT, "%,d", actual) + ")")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(tip)));
    }

    private static String dec(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String mult(double v) { return "x" + dec(v); }

    private static String pct(double v) { return "+" + dec(v * 100) + "%"; }
}
