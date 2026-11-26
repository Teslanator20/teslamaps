package com.teslamaps.features;

// Ported from SkyHanni's features/fishing/CommonSeaCreatureHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public final class CommonSeaCreatureHider {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static final Map<String, Boolean> COMMON_BY_TAG = new HashMap<>();
    private static long lastTickMs;
    private static int timer;

    private CommonSeaCreatureHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CommonSeaCreatureHider").section("Fishing").when(() -> cfg().commonSeaCreatureHider)
                .tick(CommonSeaCreatureHider::tick)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Sea Creature Hiders");
                    b.toggle("Common Sea Creature Hider", () -> c.commonSeaCreatureHider, v -> c.commonSeaCreatureHider = v)
                            .desc("Draws non-rare sea creatures see-through so the rare ones stand out.");
                    b.slider("  Opacity %", 0f, 100f, () -> c.commonSeaCreatureAlpha, v -> c.commonSeaCreatureAlpha = v, 0);
                    b.toggle("  Only While Holding A Rod", () -> c.commonSeaCreatureRodOnly, v -> c.commonSeaCreatureRodOnly = v);
                });
    }

    /** From LivingEntityAlphaMixin, every rendered living entity. 255 = untouched. */
    public static int alpha(LivingEntity e) {
        if (ids.isEmpty() || !cfg().commonSeaCreatureHider || !ids.contains(e.getId())
                || System.currentTimeMillis() - lastTickMs > 1000) return 255;
        return Math.round(Math.max(0f, Math.min(100f, cfg().commonSeaCreatureAlpha)) * 2.55f);
    }

    /** Opacity 0 = not drawn at all. */
    public static boolean shouldHide(Entity e) {
        return e instanceof LivingEntity le && cfg().commonSeaCreatureAlpha <= 0 && alpha(le) == 0;
    }

    private static void tick() {
        lastTickMs = System.currentTimeMillis();
        if (++timer % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        boolean rodOk = !cfg().commonSeaCreatureRodOnly || mc.player.getMainHandItem().getItem() instanceof FishingRodItem;
        if (rodOk && SeaCreatureList.ready() && SkyblockUtils.isInSkyblock()) {
            AABB area = mc.player.getBoundingBox().inflate(48);
            for (Entity e : mc.level.getEntities((Entity) null, area, x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component name = e.getCustomName();
                if (name == null || !isCommon(name.getString())) continue;
                LivingEntity mob = mobUnder(mc, e);
                if (mob != null) next.add(mob.getId());
            }
        }
        ids = next;
    }

    private static boolean isCommon(String raw) {
        Boolean cached = COMMON_BY_TAG.get(raw);
        if (cached != null) return cached;
        if (COMMON_BY_TAG.size() > 1024) COMMON_BY_TAG.clear();
        SeaCreatureList.Info info = lookup(StringUtil.strip(raw).trim());
        boolean common = info != null && !info.rare();
        COMMON_BY_TAG.put(raw, common);
        return common;
    }

    // "[Lv15] Sea Walker 1,500/1,500❤"
    private static SeaCreatureList.Info lookup(String tag) {
        if (!tag.endsWith("❤")) return null;
        int br = tag.indexOf("] ");
        if (br >= 0) tag = tag.substring(br + 2);
        int sp = tag.lastIndexOf(' ');
        if (sp <= 0) return null;
        String name = tag.substring(0, sp).trim();
        for (int i = 0; i < 3 && !name.isEmpty(); i++) {
            SeaCreatureList.Info info = SeaCreatureList.info(name);
            if (info != null) return info;
            int next = name.indexOf(' ');
            if (next < 0) break;
            name = name.substring(next + 1);
        }
        return null;
    }

    private static LivingEntity mobUnder(Minecraft mc, Entity tag) {
        AABB box = new AABB(tag.getX() - 1, tag.getY() - 4, tag.getZ() - 1, tag.getX() + 1, tag.getY() + 0.5, tag.getZ() + 1);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(tag, box, x -> x instanceof LivingEntity && !(x instanceof ArmorStand))) {
            if (e == mc.player || e instanceof Player p && p.getUUID().version() == 4) continue;
            double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = (LivingEntity) e; }
        }
        return best;
    }
}
