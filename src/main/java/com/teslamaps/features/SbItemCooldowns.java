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
        Feature.of("ItemCooldowns").section("Inventory").when(() -> cfg().itemCooldowns)
                .tick(SbItemCooldowns::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Item Cooldowns");
                    b.toggle("Item Cooldowns", () -> c.itemCooldowns, v -> c.itemCooldowns = v)
                            .desc("Vanilla cooldown shading on the item icon after using Spirit Leap, Shadow Fury, Giant's Sword, Treecapitator, Grappling Hook and more.")
                            .tags("cooldown", "spirit leap", "shadow fury", "giants sword");
                    b.toggle("  Crosshair Indicator", () -> c.itemCooldownsCrosshair, v -> c.itemCooldownsCrosshair = v)
                            .desc("Also fills the vanilla attack indicator while the held item cools down");
                });
    }

    private static void tick() {
        if (COOLDOWNS.isEmpty()) return;
        long now = System.currentTimeMillis();
        COOLDOWNS.values().removeIf(e -> e.start + e.duration <= now);
    }

    public static void onUseItem(Player player, ItemStack stack) {
        TeslaMapsConfig c = cfg();
        if (!c.itemCooldowns || !c.section("Inventory") || !SkyblockUtils.isInSkyblock()) return;
        String id = ItemUtil.skyblockId(stack);
        switch (id) {
            case "FIG_AXE", "FIGSTONE_AXE", "JUNGLE_AXE", "TREECAPITATOR_AXE", "SILK_EDGE_SWORD", "LEAPING_SWORD" -> start(id, 1000);
            case "GRAPPLING_HOOK" -> { if (player.fishing != null && !batArmor(player)) start(id, 2000); }
            case "ROGUE_SWORD", "SPIRIT_LEAP", "LIVID_DAGGER" -> start(id, 5000);
            case "SHADOW_FURY" -> start(id, 15000);
            case "INK_WAND", "GIANTS_SWORD" -> start(id, 30000);
            case "GREAT_SPOOK_STAFF" -> start(id, 60000);
            default -> {}
        }
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
        if (COOLDOWNS.isEmpty() || stack.isEmpty() || !cfg().itemCooldowns) return vanilla;
        Entry e = COOLDOWNS.get(ItemUtil.skyblockId(stack));
        if (e == null) return vanilla;
        return Math.max(vanilla, e.percent(System.currentTimeMillis()));
    }

    /** Gui.extractCrosshair attack strength. 1 = full, so a running cooldown shows as a filling bar. */
    public static float attackStrength(float vanilla) {
        if (COOLDOWNS.isEmpty() || !cfg().itemCooldowns || !cfg().itemCooldownsCrosshair) return vanilla;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return vanilla;
        Entry e = COOLDOWNS.get(ItemUtil.skyblockId(mc.player.getMainHandItem()));
        if (e == null) return vanilla;
        float p = e.percent(System.currentTimeMillis());
        return p <= 0f ? vanilla : 1f - p;
    }
}
