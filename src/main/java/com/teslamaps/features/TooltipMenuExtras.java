package com.teslamaps.features;

// Ported from SkyHanni's ItemStars.kt, EvolvingItems.kt, BitsPerCookieVisual.kt, StockOfStonkFeature.kt,
// FannCost.kt and Skyblocker's SupercraftReminder.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Star total in names, evolving item time, cookie bits, stonks per-stonk price, Fann cost, supercraft hint. */
public final class TooltipMenuExtras {

    private static final Pattern STARS = Pattern.compile("^(.+?) ((?:§.)*✪)+.*$");
    private static final Pattern KUUDRA = Pattern.compile("^(HOT|BURNING|FIERY|INFERNAL|)_?(AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$");
    private static final List<String> KUUDRA_TIERS = List.of("", "HOT", "BURNING", "FIERY", "INFERNAL");
    private static final Pattern COOKIE_AMOUNT = Pattern.compile("(?:Booster Cookie x|Amount: )(\\d+).*");
    private static final Pattern BITS_AVAILABLE = Pattern.compile("Bits Available: ([\\d,]+).*");
    private static final Pattern FAME_RANK = Pattern.compile("(?:Your rank|Fame Rank): (.+)");
    private static final Pattern STONK_TOP = Pattern.compile("▶ TOP ([\\d,]+) - Stock of Stonks x(\\d+)");
    private static final Pattern STONK_BID = Pattern.compile("\\s*Minimum Bid: ([\\d,]+) Coins");
    private static final Pattern FANN_COINS = Pattern.compile("^([\\d,.]+) Coins(?: \\(.*% off\\))?$");
    private static final Pattern FANN_BITS = Pattern.compile("^([\\d,.]+) Bits$");
    private static final Pattern FANN_EXP_TOTAL = Pattern.compile("Will earn a total of (.+) EXP\\.?");
    private static final Pattern FANN_EXP_DAY = Pattern.compile("EXP Per Day: (\\d[\\d,]*).*");
    private static final Pattern FANN_TAKE = Pattern.compile("Will take: (.+)");
    private static final Pattern FANN_DAYS = Pattern.compile("▶ (\\d+) Days?");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");
    private static final Map<String, Double> FAME = Map.ofEntries(
            Map.entry("new player", 1.0), Map.entry("settler", 1.1), Map.entry("citizen", 1.2),
            Map.entry("contributor", 1.3), Map.entry("philanthropist", 1.4), Map.entry("patron", 1.6),
            Map.entry("famous player", 1.8), Map.entry("attaché", 1.9), Map.entry("attache", 1.9),
            Map.entry("ambassador", 2.0), Map.entry("statesperson", 2.04), Map.entry("senator", 2.08),
            Map.entry("dignitary", 2.12), Map.entry("councilor", 2.16), Map.entry("minister", 2.2),
            Map.entry("premier", 2.24), Map.entry("chancellor", 2.28), Map.entry("supreme", 2.32));
    private static final long EVOLVE_SECONDS = 300L * 3600;

    private static int timer;
    private static volatile String fannCoinsLine = null, fannBitsLine = null;

    private TooltipMenuExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.tooltipItemStars || c.tooltipEvolvingTime || c.tooltipCookieBits || c.tooltipStonksPrice
                || c.tooltipFannCost || c.supercraftReminder;
    }

    public static void register() {
        Feature feature = Feature.of("TooltipMenuExtras").when(TooltipMenuExtras::any)
                .tick(TooltipMenuExtras::tick);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!stack.isEmpty() && !lines.isEmpty() && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void tick() {
        if (timer-- > 0) return;
        timer = 10;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            fannCoinsLine = fannBitsLine = null;
            return;
        }
        String title = StringUtil.strip(cs.getTitle().getString()).trim();
        if (c.tooltipCookieBits) scanBits(cs);
        if (c.tooltipFannCost && title.startsWith("Training Slot")) readFann(cs);
        else fannCoinsLine = fannBitsLine = null;
    }

    private static void scanBits(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = cfg();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            for (String line : ItemUtil.loreLines(st)) {
                String l = StringUtil.stripTrim(line);
                Matcher m = BITS_AVAILABLE.matcher(l);
                if (m.matches()) {
                    int v = parseInt(m.group(1));
                    if (v >= 0) c.bitsAvailable = v;
                    continue;
                }
                m = FAME_RANK.matcher(l);
                if (m.matches() && FAME.containsKey(m.group(1).trim().toLowerCase(Locale.ROOT))) c.fameRank = m.group(1).trim();
            }
        }
    }

    private static void readFann(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        if (slots.size() <= 33) return;
        List<String> begin = stripped(slots.get(22).getItem());
        if (begin.isEmpty()) return;
        double coins = 0, bits = 0;
        Double expTotal = null, expDay = null;
        double days = 0;
        for (String l : begin) {
            Matcher m;
            if ((m = FANN_COINS.matcher(l)).matches()) coins = parseDouble(m.group(1));
            else if ((m = FANN_BITS.matcher(l)).matches()) bits = parseDouble(m.group(1));
            else if ((m = FANN_EXP_TOTAL.matcher(l)).find()) expTotal = parseDouble(m.group(1));
            else if ((m = FANN_EXP_DAY.matcher(l)).find()) expDay = parseDouble(m.group(1));
            else if ((m = FANN_TAKE.matcher(l)).find()) days = durationDays(m.group(1));
        }
        if (days <= 0) {
            for (String l : stripped(slots.get(24).getItem())) {
                Matcher m = FANN_DAYS.matcher(l);
                if (m.find()) days = Integer.parseInt(m.group(1));
            }
        }
        String mode = selection(stripped(slots.get(15).getItem()));
        String type = selection(stripped(slots.get(33).getItem()));
        if (mode == null || type == null || type.equals("Free")) {
            fannCoinsLine = fannBitsLine = null;
            return;
        }
        Double exp = mode.startsWith("Until") ? (expDay == null ? null : expDay * days) : expTotal;
        if (exp == null || exp <= 0) {
            fannCoinsLine = fannBitsLine = null;
            return;
        }
        fannCoinsLine = "§7 = §6" + round2(coins / exp) + " Coins§7/§bXP";
        fannBitsLine = "§7 = §b" + round2(exp / Math.max(1.0, bits)) + " XP§7/§bBit";
    }

    private static String selection(List<String> lore) {
        for (String l : lore) if (l.startsWith("▶ ")) return l.substring(2).trim();
        return null;
    }

    private static double durationDays(String s) {
        Matcher m = DURATION_PART.matcher(s);
        double secs = 0;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            secs += switch (m.group(2)) {
                case "d" -> v * 86400;
                case "h" -> v * 3600;
                case "m" -> v * 60;
                default -> v;
            };
        }
        return secs / 86400.0;
    }

    private static List<String> stripped(ItemStack st) {
        if (st.isEmpty()) return List.of();
        List<String> raw = ItemUtil.loreLines(st);
        List<String> out = new java.util.ArrayList<>(raw.size());
        for (String l : raw) out.add(StringUtil.stripTrim(l));
        return out;
    }

    // ---------------------------------------------------------------- tooltip

    private static ItemStack lastStack;
    private static int lastKey;
    private static Component lastName;

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        if (c.tooltipItemStars && stack.getCount() == 1) {
            int key = lines.get(0).hashCode();
            if (stack != lastStack || key != lastKey) {
                lastStack = stack;
                lastKey = key;
                lastName = starName(stack, lines.get(0));
            }
            if (lastName != null) lines.set(0, lastName);
        }
        if (c.tooltipEvolvingTime) evolving(stack, lines);
        if (c.tooltipCookieBits) cookie(stack, lines);
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.strip(cs.getTitle().getString()).trim();
        if (c.tooltipStonksPrice && title.equals("Stonks Auction")) stonks(stack, lines);
        if (c.tooltipFannCost && title.startsWith("Training Slot")) fann(lines);
        if (c.supercraftReminder && title.endsWith(" Recipe")) supercraft(cs, stack, lines);
    }

    private static Component starName(ItemStack stack, Component name) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag tag = d.copyTag();
        String id = tag.getString("id").orElse("");
        if (id.isEmpty()) return null;
        int stars = tag.getInt("upgrade_level").orElse(tag.getInt("dungeon_item_level").orElse(-1));
        Matcher k = KUUDRA.matcher(id);
        if (k.matches()) {
            int tier = KUUDRA_TIERS.indexOf(k.group(1)) + 1;
            stars = Math.max(0, stars) + (tier - 1) * 10;
        }
        if (stars <= 0) return null;
        Matcher m = STARS.matcher(ChatText.formatted(name));
        if (!m.matches()) return null;
        return Component.literal(m.group(1) + " §c" + stars + "✪");
    }

    private static CustomData evoData;
    private static Component evoHeld, evoLeft;

    private static void evolving(ItemStack stack, List<Component> lines) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        if (d != evoData) {
            evoData = d;
            evoHeld = evoLeft = null;
            CompoundTag tag = d.copyTag();
            String key = switch (tag.getString("id").orElse("")) {
                case "NEW_BOTTLE_OF_JYRRE" -> "bottle_of_jyrre_seconds";
                case "DISCRITE" -> "rift_discrite_seconds";
                default -> "seconds_held";
            };
            var held = tag.getInt(key);
            if (held.isPresent()) {
                long s = held.get();
                evoHeld = Component.literal("§7Time Held: §b" + duration(s));
                evoLeft = Component.literal("§7Time Left: §b" + duration(Math.max(0, EVOLVE_SECONDS - s)));
            }
        }
        if (evoHeld == null) return;
        int at = Math.min(10, lines.size());
        lines.add(at, evoLeft);
        lines.add(at, evoHeld);
    }

    private static String duration(long s) {
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        int units = 0;
        if (d > 0) { sb.append(d).append("d "); units++; }
        if (h > 0) { sb.append(h).append("h "); units++; }
        if (m > 0 && units < 3) { sb.append(m).append("m "); units++; }
        if ((sec > 0 || sb.isEmpty()) && units < 3) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    private static void cookie(ItemStack stack, List<Component> lines) {
        if (!"BOOSTER_COOKIE".equals(ItemUtil.skyblockId(stack))) return;
        String name = ChatText.formatted(stack.getHoverName());
        if (name.startsWith("§dBooster Cookie") || name.startsWith("§eBooster Cookie")) return;
        int amount = 1, at = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = COOKIE_AMOUNT.matcher(StringUtil.stripTrim(lines.get(i).getString()));
            if (m.matches()) {
                amount = Math.max(1, Integer.parseInt(m.group(1)));
                at = i;
                break;
            }
        }
        int index = at >= 0 ? at + 1 : Math.min(1, lines.size());
        TeslaMapsConfig c = cfg();
        double mult = FAME.getOrDefault(c.fameRank.toLowerCase(Locale.ROOT), 1.0);
        long gain = Math.round(4800 * mult) * amount;
        lines.add(index++, Component.empty());
        lines.add(index++, Component.literal("§8‣ §7Cookie Buff for §b" + (4 * amount) + " §7days"));
        lines.add(index++, Component.literal("§8‣ §7Gain §b" + commas(gain) + " Bits"
                + (c.fameRank.isEmpty() ? " §8(open SkyBlock menu for fame rank)" : "")));
        if (c.bitsAvailable >= 0) lines.add(index, Component.literal("§8‣ §7Available Bits: §3" + commas(c.bitsAvailable)
                + " §6→ §3" + commas(c.bitsAvailable + gain)));
    }

    private static void stonks(ItemStack stack, List<Component> lines) {
        if (!StringUtil.stripTrim(stack.getHoverName().getString()).equals("Stonks Auction")) return;
        int reward = 0, best = -1;
        long bestRatio = Long.MAX_VALUE;
        long[] ratios = new long[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String l = StringUtil.strip(lines.get(i).getString());
            Matcher m = STONK_TOP.matcher(l);
            if (m.find()) {
                reward = Integer.parseInt(m.group(2));
                continue;
            }
            m = STONK_BID.matcher(l);
            if (m.matches()) {
                long cost = Math.max(2_000_000L, parseLong(m.group(1)));
                long ratio = cost / Math.max(1, reward);
                ratios[i] = ratio;
                if (ratio < bestRatio) {
                    bestRatio = ratio;
                    best = i;
                }
            }
        }
        for (int i = 0; i < ratios.length; i++) {
            if (ratios[i] == 0) continue;
            ChatFormatting col = i == best ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            lines.set(i, lines.get(i).copy().append(Component.literal(" (paying ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(commas(ratios[i])).withStyle(col))
                    .append(Component.literal(" per)").withStyle(ChatFormatting.GRAY)));
        }
    }

    private static void fann(List<Component> lines) {
        String coins = fannCoinsLine, bits = fannBitsLine;
        if (coins == null || bits == null) return;
        for (int i = 0; i < lines.size(); i++) {
            String l = StringUtil.stripTrim(lines.get(i).getString());
            if (FANN_COINS.matcher(l).matches()) lines.add(++i, Component.literal(coins));
            else if (FANN_BITS.matcher(l).matches()) lines.add(++i, Component.literal(bits));
        }
    }

    private static void supercraft(AbstractContainerScreen<?> cs, ItemStack stack, List<Component> lines) {
        Slot focused = ((com.teslamaps.mixin.HandledScreenAccessor) cs).getFocusedSlot();
        if (focused == null || focused.index != 32 || !stack.is(Items.GOLDEN_PICKAXE)) return;
        var slots = cs.getMenu().slots;
        if (slots.size() <= 25) return;
        String uuid = ItemUtil.itemUuid(slots.get(25).getItem());
        if (uuid != null && !uuid.isEmpty()) return;
        if (lines.size() < 2) return;
        int index = lines.size() - 1;
        if (StringUtil.stripTrim(lines.get(lines.size() - 2).getString()).equals("Recipe not unlocked!")) index--;
        lines.add(index, Component.literal("Shift-Click to maximize the amount!").withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- numbers

    private static String commas(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static String round2(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return 0; }
    }
}
