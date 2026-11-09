package com.teslamaps.features.croesus;

// Ported from Skyblocker's ChestValue.computeKuudraKeyPrice / Kuudra.checkKuudraKeyShop and Odin's Vesuvius
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a Kuudra key really costs: coins + Mycelium / Red Sand + 2 Corrupted Nether Stars. */
public final class KuudraKeys {

    // most specific first, "Hot Kuudra Key" also contains "Kuudra Key"
    static final String[] NAMES = {
            "Infernal Kuudra Key", "Fiery Kuudra Key", "Burning Kuudra Key", "Hot Kuudra Key", "Kuudra Key"};
    private static final int[] DEFAULT_COINS = {3_000_000, 1_500_000, 750_000, 400_000, 200_000};
    private static final int[] MATERIAL = {80, 40, 16, 4, 2};

    private static final Pattern SHOP = Pattern.compile("^(Mage|Barbarian) Shop$");
    private static final Pattern COINS = Pattern.compile("^([0-9,]+) Coins$");

    private static int ticks;

    private KuudraKeys() {}

    public static void register() {
        Feature.of("KuudraKeys").when(() -> TeslaMapsConfig.get().croesusProfitOverlay).tick(KuudraKeys::tick);
    }

    /** Index into NAMES for the key a lore line names, -1 if none. */
    static int keyIndex(String line) {
        for (int i = 0; i < NAMES.length; i++) if (line.contains(NAMES[i])) return i;
        return -1;
    }

    /** Full key price, or -1 when a material price is missing. */
    static double cost(int index, int source) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Integer shop = c.kuudraKeyCoins.get(NAMES[index]);
        double coins = shop != null ? shop : DEFAULT_COINS[index];
        Double mycelium = price("ENCHANTED_MYCELIUM", source);
        Double sand = price("ENCHANTED_RED_SAND", source);
        Double material = switch (c.kuudraFaction) {
            case "Mage" -> mycelium;
            case "Barbarian" -> sand;
            default -> mycelium == null ? sand : sand == null ? mycelium : Math.min(mycelium, sand);
        };
        Double star = price("CORRUPTED_NETHER_STAR", source);
        if (material == null || star == null) return -1;
        return coins + material * MATERIAL[index] + star * 2;
    }

    private static Double price(String id, int source) {
        return PriceManager.marketPriceOrNull(id, source);
    }

    /** True if the player carries the given key. */
    static boolean hasKey(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true;
        if (mc.player != keysPlayer || mc.player.tickCount != keysTick) {
            keysPlayer = mc.player;
            keysTick = mc.player.tickCount;
            java.util.Arrays.fill(keys, false);
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                String name = StringUtil.stripTrim(s.getHoverName().getString());
                for (int k = 0; k < NAMES.length; k++) if (NAMES[k].equals(name)) keys[k] = true;
            }
        }
        return keys[index];
    }

    private static final boolean[] keys = new boolean[NAMES.length];
    private static Object keysPlayer;
    private static int keysTick;

    static void tick() {
        if (++ticks < 10) return;
        ticks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        Matcher shop = SHOP.matcher(StringUtil.stripTrim(cs.getTitle().getString()));
        if (!shop.matches()) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean changed = !shop.group(1).equals(c.kuudraFaction);
        c.kuudraFaction = shop.group(1);
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            String name = StringUtil.stripTrim(item.getHoverName().getString());
            int idx = -1;
            for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) idx = i;
            if (idx < 0) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null) continue;
            for (Component line : lore.lines()) {
                Matcher m = COINS.matcher(StringUtil.stripTrim(line.getString()));
                if (!m.matches()) continue;
                try {
                    int coins = Integer.parseInt(m.group(1).replace(",", ""));
                    Integer old = c.kuudraKeyCoins.put(NAMES[idx], coins);
                    if (old == null || old != coins) changed = true;
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        if (changed) TeslaMapsConfig.save();
    }
}
