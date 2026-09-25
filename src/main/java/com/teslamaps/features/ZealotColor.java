package com.teslamaps.features;

// SkyblockAddons' ModelEndermanHook / RenderEndermanHook (zealot recolor)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;

/** Tints the model of endermen that carry a Zealot name tag. */
public final class ZealotColor {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;
    private static int color = 0xFF55FF55;
    private static String parsed;

    private ZealotColor() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ZealotColor").section("The End").when(() -> cfg().zealotColor)
                .tick(ZealotColor::tick)
                .config("The End", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Zealots");
                    b.toggle("Zealot Color", () -> c.zealotColor, v -> c.zealotColor = v)
                            .desc("Tints zealot endermen in a color of your choice.");
                    b.color("  Color", () -> c.zealotColorValue, v -> c.zealotColorValue = v);
                    b.toggle("  Chroma", () -> c.zealotColorChroma, v -> c.zealotColorChroma = v)
                            .desc("Cycles through the rainbow instead of the fixed color.");
                });
    }

    /** Model tint for this enderman, 0 = vanilla. Called while extracting render state. */
    public static int tint(EnderMan e) {
        if (ids.isEmpty() || !ids.contains(e.getId())) return 0;
        TeslaMapsConfig c = cfg();
        if (!c.zealotColor || !c.section("The End")) return 0;
        if (c.zealotColorChroma) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f;
            return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF);
        }
        return color;
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        if (!c.zealotColorValue.equals(parsed)) {
            parsed = c.zealotColorValue;
            try {
                String hex = parsed.length() > 6 ? parsed.substring(parsed.length() - 6) : parsed;
                color = 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException ex) {
                color = 0xFF55FF55;
            }
        }
        if (++timer % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.isInSkyblock() && (SkyblockUtils.isInArea("The End") || SkyblockUtils.isInArea("Dragon's Nest"))) {
            for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(64),
                    x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component n = e.getCustomName();
                if (n == null || !StringUtil.strip(n.getString()).contains("Zealot")) continue;
                AABB box = new AABB(e.getX() - 1, e.getY() - 3.5, e.getZ() - 1, e.getX() + 1, e.getY() + 0.5, e.getZ() + 1);
                EnderMan best = null;
                double bestD = Double.MAX_VALUE;
                for (EnderMan m : mc.level.getEntitiesOfClass(EnderMan.class, box)) {
                    double d = m.distanceToSqr(e);
                    if (d < bestD) { bestD = d; best = m; }
                }
                if (best != null) next.add(best.getId());
            }
        }
        ids = next;
    }
}
