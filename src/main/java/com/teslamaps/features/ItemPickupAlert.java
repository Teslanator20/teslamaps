package com.teslamaps.features;

// Ported from Bax's InventoryDiffTracker.java + ItemPickupTrigger.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Diffs the 36 inventory slots every tick and alerts when a watched item shows up. */
public final class ItemPickupAlert {

    private static final long COOLDOWN_MS = 3000;

    private static Map<String, Integer> previous = new HashMap<>();
    private static Object lastLevel;
    private static long cooldownUntil;
    private static final Set<String> recent = new LinkedHashSet<>();

    private ItemPickupAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ItemPickupAlert").section("Inventory").when(() -> cfg().itemPickupAlert)
                .tickAlways(ItemPickupAlert::tick)
                .config("Inventory", b -> {
                    b.label("Item Pickup Alert");
                    b.toggle("Item Pickup Alert", () -> cfg().itemPickupAlert, v -> cfg().itemPickupAlert = v)
                            .desc("Alert when a watched item lands in your inventory, also without a drop chat line");
                    b.searchList("  Watched Items", ItemPickupAlert::options,
                            id -> cfg().itemPickupAlertNames.contains(id),
                            id -> { if (!cfg().itemPickupAlertNames.remove(id)) cfg().itemPickupAlertNames.add(id); });
                    b.info("List shows your inventory and recent pickups; name contains, case-insensitive");
                    b.toggle("  Title", () -> cfg().itemPickupAlertTitle, v -> cfg().itemPickupAlertTitle = v);
                    b.toggle("  Sound", () -> cfg().itemPickupAlertSound, v -> cfg().itemPickupAlertSound = v);
                });
    }

    private static List<String> options() {
        Set<String> out = new LinkedHashSet<>(cfg().itemPickupAlertNames);
        out.addAll(recent);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) out.add(name(s));
            }
        }
        return new ArrayList<>(out);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            previous.clear();
            lastLevel = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            cooldownUntil = now + COOLDOWN_MS;
            previous = snapshot(mc.player.getInventory());
            return;
        }
        Map<String, Integer> current = snapshot(mc.player.getInventory());
        Map<String, Integer> old = previous;
        previous = current;
        if (wiped(old, current)) {
            cooldownUntil = now + COOLDOWN_MS;
            return;
        }
        if (now < cooldownUntil || mc.gui.screen() instanceof AbstractContainerScreen<?> || !SkyblockUtils.isInSkyblock()) return;
        for (Map.Entry<String, Integer> e : current.entrySet()) {
            int delta = e.getValue() - old.getOrDefault(e.getKey(), 0);
            if (delta <= 0) continue;
            recent.remove(e.getKey());
            recent.add(e.getKey());
            if (recent.size() > 40) recent.remove(recent.iterator().next());
            if (watched(e.getKey())) alert(e.getKey(), delta);
        }
    }

    private static boolean wiped(Map<String, Integer> old, Map<String, Integer> current) {
        if (old.isEmpty()) return false;
        int a = 0, b = 0;
        for (int v : old.values()) a += v;
        for (int v : current.values()) b += v;
        return a > 5 && b < a / 2;
    }

    private static Map<String, Integer> snapshot(Inventory inv) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) map.merge(name(s), s.getCount(), Integer::sum);
        }
        return map;
    }

    private static String name(ItemStack s) {
        return StringUtil.stripTrim(s.getHoverName().getString());
    }

    private static boolean watched(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String w : cfg().itemPickupAlertNames) {
            if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static void alert(String name, int count) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fPicked up §e" + count + "x " + name));
        if (c.itemPickupAlertTitle) {
            mc.gui.hud.setTimes(0, 30, 10);
            mc.gui.hud.setTitle(Component.literal("§e" + name));
        }
        if (c.itemPickupAlertSound) LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }
}
