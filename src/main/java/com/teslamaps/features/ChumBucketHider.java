package com.teslamaps.features;

// Ported from SkyHanni's features/fishing/ChumBucketHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class ChumBucketHider {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;
    private static long tickAt;

    private ChumBucketHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChumBucketHider").section("Fishing").when(() -> cfg().chumBucketHider)
                .tick(ChumBucketHider::tick)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Chum Bucket Hider", () -> c.chumBucketHider, v -> c.chumBucketHider = v)
                            .desc("Hides the \"<name>'s Chum Bucket\" and \"/10 Chums\" name tags.");
                    b.toggle("  Hide Bucket Too", () -> c.chumBucketHideBucket, v -> c.chumBucketHideBucket = v);
                    b.toggle("  Hide Own Buckets", () -> c.chumBucketHideOwn, v -> c.chumBucketHideOwn = v);
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !ids.isEmpty() && cfg().chumBucketHider && ids.contains(e.getId())
                && System.currentTimeMillis() - tickAt < 1500;
    }

    private static void tick() {
        tickAt = System.currentTimeMillis();
        if (++timer % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.isInSkyblock()) {
            TeslaMapsConfig c = cfg();
            String me = mc.player.getGameProfile().name();
            List<Entity> stands = mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(64), x -> x instanceof ArmorStand);
            List<Entity> titles = new ArrayList<>();
            for (Entity e : stands) {
                String n = name(e);
                if (n == null || !(n.endsWith("'s Chum Bucket") || n.endsWith("'s Chumcap Bucket"))) continue;
                if (n.contains(me) && !c.chumBucketHideOwn) continue;
                titles.add(e);
                next.add(e.getId());
            }
            if (!titles.isEmpty()) {
                for (Entity e : stands) {
                    if (next.contains(e.getId())) continue;
                    String n = name(e);
                    boolean hide = n != null && n.contains("/10 Chums")
                            || c.chumBucketHideBucket && isBucket((ArmorStand) e);
                    if (hide && under(e, titles)) next.add(e.getId());
                }
            }
        }
        ids = next;
    }

    private static String name(Entity e) {
        Component n = e.getCustomName();
        return n == null ? null : StringUtil.strip(n.getString()).trim();
    }

    private static boolean isBucket(ArmorStand s) {
        return isBucket(s.getItemBySlot(EquipmentSlot.HEAD)) || isBucket(s.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    private static boolean isBucket(ItemStack st) {
        if (st.isEmpty()) return false;
        String n = st.getHoverName().getString();
        return n.equals("Empty Chum Bucket") || n.equals("Empty Chumcap Bucket");
    }

    private static boolean under(Entity e, List<Entity> titles) {
        for (Entity t : titles) {
            if (Math.abs(t.getX() - e.getX()) < 0.1 && Math.abs(t.getZ() - e.getZ()) < 0.1) return true;
        }
        return false;
    }
}
