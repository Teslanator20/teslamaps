package com.teslamaps.features.rift;

// After SkyHanni's ShowMotesNpcSellPrice.kt / Skyblocker's MotesTooltip.java, stack detection from Skyblocker's McGrubberUpdater.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MotesPrice {

    private static final Pattern ORB_PICKUP = Pattern.compile("ORB! Picked up \\+(\\d+) Motes, recovered \\+2. Rift Time!");
    private static final Pattern GRUBBER_STACKS = Pattern.compile("You have (\\d) Grubber Stacks");
    private static final Pattern LORE_PRICE = Pattern.compile("(\\d+(?:\\.\\d)?) Motes");
    private static final Pattern PROGRESS = Pattern.compile("Total Progress: (\\d+)%");

    private static Feature feature;
    private static final Map<ItemStack, String> cache = new IdentityHashMap<>();
    private static Object lastScreen;
    private static int screenTicks;

    private MotesPrice() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        feature = Feature.of("MotesPrice").section("Rift").when(() -> cfg().riftMotesPrice)
                .tick(MotesPrice::tick)
                .serverChat(MotesPrice::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Motes Price in Tooltip", () -> c.riftMotesPrice, v -> c.riftMotesPrice = v)
                            .desc("In the Rift every item shows what the Motes NPC pays for it, +5% per McGrubber burger stack.");
                    b.slider("  McGrubber Stacks", 0f, 5f, () -> (float) c.riftMcGrubberStacks, v -> c.riftMcGrubberStacks = Math.round(v));
                    b.toggle("  Auto-Detect McGrubber Stacks", () -> c.riftMcGrubberAuto, v -> c.riftMcGrubberAuto = v)
                            .desc("Reads the stacks from the Motes Grubber menu, the Consumable Items menu (/sblevels) and orb pickups.");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !RiftZone.inRift() || !feature.active()) return;
            String line = cache.get(stack);
            if (line == null) {
                line = build(stack);
                if (cache.size() > 64) cache.clear();
                cache.put(stack, line);
            }
            if (!line.isEmpty()) lines.add(Component.literal(line));
        });
    }

    public static double multiplier() {
        return 1 + Math.max(0, cfg().riftMcGrubberStacks) * 0.05;
    }

    /** Motes for the whole stack incl. burger stacks, 0 = none / not sellable. */
    public static double value(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        double base = SkyblockItems.motesPrice(ItemUtil.skyblockId(stack));
        if (base <= 0) return 0;
        if (transferred(stack) && ItemUtil.loreContains(stack, "Rift-Export")) return 0;
        return base * multiplier() * stack.getCount();
    }

    private static boolean transferred(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr("rift_transferred", false);
    }

    static String num(double v) {
        return v == Math.floor(v) ? String.format(Locale.ENGLISH, "%,d", (long) v) : String.format(Locale.ENGLISH, "%,.1f", v);
    }

    private static String build(ItemStack stack) {
        double total = value(stack);
        if (total <= 0) return "";
        int n = stack.getCount();
        String out = "§dMotes Price: §3" + num(total) + " Motes";
        if (n > 1) out += " §7(" + num(total / n) + " each)";
        return out;
    }

    private static void tick() {
        SkyblockItems.load();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            screenTicks = 0;
            cache.clear();
        }
        if (!cfg().riftMcGrubberAuto || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (++screenTicks != 10 && screenTicks != 30) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (RiftZone.inRift() && title.equals("Motes Grubber")) fromGrubber(cs);
        else if (title.endsWith("Consumable Items")) fromConsumables(cs);
    }

    private static void fromGrubber(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            ItemStack item = slot.getItem();
            double base = SkyblockItems.motesPrice(ItemUtil.skyblockId(item));
            if (base <= 0) continue;
            for (String line : ItemUtil.loreLines(item)) {
                Matcher m = LORE_PRICE.matcher(line);
                if (!m.find()) continue;
                double price = Double.parseDouble(m.group(1));
                set((int) Math.round(20 * price / base - 20));
                return;
            }
        }
    }

    private static void fromConsumables(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            ItemStack item = slot.getItem();
            if (!"MCGRUBBER_BURGER".equals(ItemUtil.skyblockId(item))) continue;
            for (String line : ItemUtil.loreLines(item)) {
                Matcher m = PROGRESS.matcher(line);
                if (m.find()) {
                    set(Integer.parseInt(m.group(1)) / 20);
                    return;
                }
            }
        }
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Motes") < 0 && raw.indexOf("Grubber") < 0) return;
        String s = StringUtil.stripTrim(raw);
        Matcher m = GRUBBER_STACKS.matcher(s);
        if (m.find()) {
            set(Integer.parseInt(m.group(1)));
            return;
        }
        if (!cfg().riftMcGrubberAuto || !RiftZone.inRift()) return;
        m = ORB_PICKUP.matcher(s);
        if (m.matches()) set((Integer.parseInt(m.group(1)) - 5) / 60);
    }

    private static void set(int stacks) {
        if (stacks < 0 || stacks > 5 || stacks == cfg().riftMcGrubberStacks) return;
        cfg().riftMcGrubberStacks = stacks;
        TeslaMapsConfig.save();
        cache.clear();
    }
}
