package com.teslamaps.features;

// Ported from Skyblocker's ItemCooldowns.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class SbItemCooldowns {

    private record Entry(long start, int duration) {
        float percent(long now) {
            long left = start + duration - now;
            return left <= 0 ? 0f : (float) left / duration;
        }
    }

    private static final Map<String, Entry> COOLDOWNS = new HashMap<>();

    private SbItemCooldowns() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ItemCooldowns").section("Inventory").when(SbItemCooldowns::on)
                .tick(SbItemCooldowns::tick);
    }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.itemCooldowns && c.abilityCooldownShade;
    }

    /** Rows under AbilityCooldowns' "Shade Item Icon". */
    static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("  Extra Items (Treecap, Grappling Hook, ...)", () -> c.itemCooldowns, v -> c.itemCooldowns = v)
                .desc("Fixed cooldowns for items whose lore has none: Treecapitator, Grappling Hook, Rogue Sword, Livid Dagger, Ink Wand and more.")
                .tags("cooldown", "treecapitator", "grappling hook", "item cooldowns");
        b.toggle("    Crosshair Indicator", () -> c.itemCooldownsCrosshair, v -> c.itemCooldownsCrosshair = v)
                .desc("Also fills the vanilla attack indicator while the held item cools down");
        b.toggle("    Treecapitator Break Cooldown", () -> c.treecapCooldown, v -> c.treecapCooldown = v)
                .desc("Jungle Axe / Treecapitator: 2s cooldown after breaking a log")
                .tags("treecapitator", "jungle axe");
        b.toggle("      Monkey Pet Reduction", () -> c.treecapMonkey, v -> c.treecapMonkey = v)
                .desc("Legendary Monkey: up to x0.6 at level 100");
        b.toggle("      Predict And Shorten", () -> c.treecapPredict, v -> c.treecapPredict = v)
                .desc("Subtracts the time spent breaking the log, at least 400ms left");
    }

    private static void tick() {
        if (COOLDOWNS.isEmpty()) return;
        long now = System.currentTimeMillis();
        COOLDOWNS.values().removeIf(e -> e.start + e.duration <= now);
    }

    public static void onUseItem(Player player, ItemStack stack) {
        TeslaMapsConfig c = cfg();
        if (!on() || !c.section("Inventory") || !SkyblockUtils.isInSkyblock()) return;
        String id = ItemUtil.skyblockId(stack);
        switch (id) {
            case "FIG_AXE", "FIGSTONE_AXE", "JUNGLE_AXE", "TREECAPITATOR_AXE", "SILK_EDGE_SWORD", "LEAPING_SWORD" -> start(id, 1000);
            case "GRAPPLING_HOOK" -> { if (player.fishing != null && !batArmor(player)) start(id, 2000); }
            case "ROGUE_SWORD", "LIVID_DAGGER" -> start(id, 5000);
            case "INK_WAND" -> start(id, 30000);
            case "GREAT_SPOOK_STAFF" -> start(id, 60000);
            default -> {}
        }
    }

    // SkyblockAddons' treecapitator cooldown (onBlockBreak + PlayerControllerMPHook)
    private static net.minecraft.core.BlockPos breakPos;
    private static long breakStart;

    public static void onStartBreak(net.minecraft.core.BlockPos pos) {
        if (!cfg().treecapCooldown) return;
        if (!pos.equals(breakPos)) {
            breakPos = pos.immutable();
            breakStart = System.currentTimeMillis();
        }
    }

    public static void onBreak(net.minecraft.core.BlockPos pos) {
        TeslaMapsConfig c = cfg();
        if (!c.treecapCooldown || !on() || !c.section("Inventory")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) return;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (!id.equals("JUNGLE_AXE") && !id.equals("TREECAPITATOR_AXE")) return;
        long took = pos.equals(breakPos) ? System.currentTimeMillis() - breakStart : 0;
        breakPos = null;
        double ms = 2000;
        if (c.treecapMonkey && "MONKEY".equalsIgnoreCase(com.teslamaps.features.pets.PetTracker.internalName())
                && "LEGENDARY".equalsIgnoreCase(com.teslamaps.features.pets.PetTracker.rarity())) {
            ms *= 1 - 0.004 * Math.min(100, Math.max(0, com.teslamaps.features.pets.PetTracker.level()));
        }
        if (c.treecapPredict) ms = Math.max(400, ms - took);
        start(id, (int) ms);
    }

    private static void start(String id, int ms) {
        long now = System.currentTimeMillis();
        Entry e = COOLDOWNS.get(id);
        if (e != null && e.start + e.duration > now) return;
        COOLDOWNS.put(id, new Entry(now, ms));
    }

    private static boolean batArmor(Player p) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            if (!ItemUtil.skyblockId(p.getItemBySlot(s)).startsWith("BAT_PERSON_")) return false;
        }
        return true;
    }

    /** GuiGraphicsExtractor.itemCooldown, per drawn item. */
    public static float percent(ItemStack stack, float vanilla) {
        vanilla = AbilityCooldowns.percent(stack, vanilla);
        if (COOLDOWNS.isEmpty() || stack.isEmpty() || !on()) return vanilla;
        Entry e = COOLDOWNS.get(ItemUtil.skyblockId(stack));
        if (e == null) return vanilla;
        return Math.max(vanilla, e.percent(System.currentTimeMillis()));
    }

    /** Gui.extractCrosshair attack strength. 1 = full, so a running cooldown shows as a filling bar. */
    public static float attackStrength(float vanilla) {
        if (COOLDOWNS.isEmpty() || !on() || !cfg().itemCooldownsCrosshair) return vanilla;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return vanilla;
        Entry e = COOLDOWNS.get(ItemUtil.skyblockId(mc.player.getMainHandItem()));
        if (e == null) return vanilla;
        float p = e.percent(System.currentTimeMillis());
        return p <= 0f ? vanilla : 1f - p;
    }
}
