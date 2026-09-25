package com.teslamaps.features.mining;

// After SkyOcean's CorpseKeyAnnouncement
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SackApi;
import com.teslamaps.features.StorageCache;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/** When the mineshaft corpses show up: per type the corpse count and the keys you own. */
public final class CorpseKeyAnnounce {

    private enum Type {
        TUNGSTEN("§7", "TUNGSTEN_KEY", "Tungsten Key"),
        UMBER("§6", "UMBER_KEY", "Umber Key"),
        VANGUARD("§b", "SKELETON_KEY", "Skeleton Key");

        final String color, keyId, keyName;

        Type(String color, String keyId, String keyName) {
            this.color = color;
            this.keyId = keyId;
            this.keyName = keyName;
        }
    }

    private static final Map<UUID, Type> corpses = new HashMap<>();
    private static Object lastLevel;
    private static long firstSeen;
    private static boolean announced;
    private static int ticks;

    private CorpseKeyAnnounce() {}

    public static void register() {
        Feature.of("CorpseKeyAnnounce").section("Mining").when(() -> TeslaMapsConfig.get().corpseKeyAnnounce)
                .tick(CorpseKeyAnnounce::tick);
    }

    private static Type typeOf(String helmet) {
        return switch (helmet) {
            case "MINERAL_HELMET" -> Type.TUNGSTEN;
            case "ARMOR_OF_YOG_HELMET", "YOG_HELMET" -> Type.UMBER;
            case "VANGUARD_HELMET" -> Type.VANGUARD;
            default -> null;
        };
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            corpses.clear();
            announced = false;
            firstSeen = 0;
        }
        if (announced || ++ticks % 10 != 0) return;
        if (!com.teslamaps.utils.SkyblockUtils.getArea().contains("Mineshaft")) return;
        for (Entity e : com.teslamaps.utils.EntityCache.armorStands()) {
            if (!(e instanceof ArmorStand as) || as.isRemoved()) continue;
            String id = ItemUtil.skyblockId(as.getItemBySlot(EquipmentSlot.HEAD));
            if (id == null) continue;
            if (id.equals("LAPIS_ARMOR_HELMET") && firstSeen == 0) firstSeen = System.currentTimeMillis();
            Type t = typeOf(id);
            if (t == null) continue;
            if (firstSeen == 0) firstSeen = System.currentTimeMillis();
            corpses.putIfAbsent(as.getUUID(), t);
        }
        if (firstSeen == 0 || System.currentTimeMillis() - firstSeen < 3000) return;
        announced = true;
        if (corpses.isEmpty()) return;
        send(mc);
    }

    private static void send(Minecraft mc) {
        Map<Type, Integer> count = new LinkedHashMap<>();
        for (Type t : Type.values()) count.put(t, 0);
        for (Type t : corpses.values()) count.merge(t, 1, Integer::sum);
        StringBuilder sb = new StringBuilder("§b[TeslaMaps] §7Corpse Keys §8| ");
        boolean first = true;
        for (Map.Entry<Type, Integer> e : count.entrySet()) {
            if (e.getValue() == 0) continue;
            Type t = e.getKey();
            long keys = keys(mc, t);
            if (!first) sb.append("§7, ");
            first = false;
            sb.append("§f").append(e.getValue()).append(' ').append(t.color)
                    .append(t.name().charAt(0)).append(t.name().substring(1).toLowerCase())
                    .append(" §7(").append(keys < e.getValue() ? "§c" : "§a").append(keys).append(" §7keys)");
        }
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(sb.toString()));
    }

    private static long keys(Minecraft mc, Type t) {
        long n = Math.max(0, SackApi.amount(t.keyName));
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) n += count(inv.getItem(i), t);
        for (int i = 1; i <= 9; i++) n += stored("epage_" + i, t);
        for (int i = 1; i <= 18; i++) n += stored("backpack_" + i, t);
        return n;
    }

    private static long stored(String key, Type t) {
        var items = StorageCache.items(key);
        if (items == null) return 0;
        long n = 0;
        for (ItemStack s : items.values()) n += count(s, t);
        return n;
    }

    private static int count(ItemStack s, Type t) {
        return s != null && !s.isEmpty() && t.keyId.equals(ItemUtil.skyblockId(s)) ? s.getCount() : 0;
    }
}
