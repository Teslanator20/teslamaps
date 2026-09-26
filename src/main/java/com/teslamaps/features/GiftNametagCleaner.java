package com.teslamaps.features;

// Ported from SkyHanni's features/misc/GiftCleanDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class GiftNametagCleaner {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;

    private GiftNametagCleaner() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GiftNametagCleaner").when(() -> cfg().giftCleanNametags)
                .tick(GiftNametagCleaner::tick)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Name Tags");
                    b.toggle("Clean Gift Name Tags", () -> c.giftCleanNametags, v -> c.giftCleanNametags = v)
                            .desc("Placed gifts only keep CLICK TO OPEN, the From: / To: tags are hidden.");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !ids.isEmpty() && cfg().giftCleanNametags && ids.contains(e.getId());
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.inSkyblockCached()) {
            for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(48),
                    x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component n = e.getCustomName();
                if (n == null) continue;
                String s = StringUtil.stripTrim(n.getString());
                if (s.startsWith("From:") || s.startsWith("To:")) next.add(e.getId());
            }
        }
        ids = next;
    }
}
