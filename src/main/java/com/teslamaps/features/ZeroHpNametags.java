package com.teslamaps.features;

// Ported from NoammAddons' features/impl/visual/RenderOptimizer.kt (hide0HealthNames)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class ZeroHpNametags {

    private static final Pattern ZERO_HP = Pattern.compile("\\[Lv\\d+] .+ 0/.+❤");
    private static final Map<String, Boolean> CACHE = new HashMap<>();

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;

    private ZeroHpNametags() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ZeroHpNametags").when(() -> cfg().hideZeroHpNametags)
                .tick(ZeroHpNametags::tick)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Hide 0 HP Nametags", () -> c.hideZeroHpNametags, v -> c.hideZeroHpNametags = v)
                            .desc("Hides mob name tags that already show 0 health, like [Lv10] Zombie 0/100❤.");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !ids.isEmpty() && cfg().hideZeroHpNametags && ids.contains(e.getId());
    }

    private static void tick() {
        if (++timer % 3 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.isInSkyblock()) {
            for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(48),
                    x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component n = e.getCustomName();
                if (n != null && isZero(n.getString())) next.add(e.getId());
            }
        }
        ids = next;
    }

    private static boolean isZero(String raw) {
        Boolean cached = CACHE.get(raw);
        if (cached != null) return cached;
        if (CACHE.size() > 2048) CACHE.clear();
        String s = StringUtil.strip(raw).trim();
        boolean zero = s.equals("0") || ZERO_HP.matcher(s).matches();
        CACHE.put(raw, zero);
        return zero;
    }
}
