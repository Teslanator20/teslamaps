package com.teslamaps.features;

// Ported from DulkirMod's features/NametagCleaner.kt (hideArachneTags)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class ArachneTagHider {

    private static final String[] LOOT = {"Luxurious Spool", "String", "Arachne Fragment", "Spider Eye"};

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;

    private ArachneTagHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ArachneTagHider").when(() -> cfg().hideArachneLootTags)
                .tick(ArachneTagHider::tick)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Name Tags");
                    b.toggle("Hide Arachne Loot Tags", () -> c.hideArachneLootTags, v -> c.hideArachneLootTags = v)
                            .desc("Spider's Den: hides the floating Luxurious Spool / String / Arachne Fragment / Spider Eye tags.");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !ids.isEmpty() && cfg().hideArachneLootTags && ids.contains(e.getId());
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.isInArea("Spider's Den")) {
            for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(64),
                    x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component n = e.getCustomName();
                if (n != null && isLoot(StringUtil.strip(n.getString()))) next.add(e.getId());
            }
        }
        ids = next;
    }

    private static boolean isLoot(String s) {
        for (String l : LOOT) if (s.contains(l)) return true;
        return false;
    }
}
