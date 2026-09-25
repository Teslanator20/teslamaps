package com.teslamaps.features.theend;

// Stage colors from SkyHanni's MobHighlight, teleport stop after Zen's NoEndermanTP
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EndermanQol {

    private static final int SPECIAL = 0xFFAA0000;
    private static final int CHEST = 0xFF55FF55;
    private static final long HOLD_MS = 3000;

    private static final class Held {
        final long since;
        Vec3 offset = Vec3.ZERO;
        Held(long since) { this.since = since; }
    }

    private static final Int2ObjectOpenHashMap<Held> held = new Int2ObjectOpenHashMap<>();

    private static Object lastLevel;

    private EndermanQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EndermanQol").section("The End")
                .when(() -> cfg().endermanStopTeleport)
                .tick(EndermanQol::tick)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Endermen");
                    b.toggle("Hide Enderman Hurt Tint", () -> c.endermanNoHurtTint, v -> c.endermanNoHurtTint = v)
                            .desc("No red flash when you hit an enderman, Zealot or Voidgloom.")
                            .tags("enderman", "zealot", "voidgloom", "hurt", "red", "overlay");
                    b.toggle("Enderman Stage Colors", () -> c.endermanStageColors, v -> c.endermanStageColors = v)
                            .desc("Tints Special Zealots (portal frame) dark red and chest Zealots (ender chest) green.")
                            .tags("enderman", "zealot", "special", "chest", "highlight");
                    b.toggle("Stop Enderman Teleport", () -> c.endermanStopTeleport, v -> c.endermanStopTeleport = v)
                            .desc("Endermen and Voidgloom stay where they were instead of jumping away. The server still "
                                    + "moves them, so for 3 s you may hit where the model is, not where the mob is.")
                            .tags("enderman", "zealot", "voidgloom", "teleport");
                    b.info("Enderman sounds: Sounds > Mute Endermen In The End");
                });
    }

    private static boolean active() {
        return SkyblockUtils.isInSkyblock() && cfg().section("The End");
    }

    /** From EndermanTintMixin. */
    public static boolean hideHurtTint() {
        return cfg().endermanNoHurtTint && active();
    }

    /** From EndermanTintMixin, 0 = no stage color. */
    public static int tint(EnderMan e) {
        if (!cfg().endermanStageColors || !active()) return 0;
        BlockState held = e.getCarriedBlock();
        if (held == null) return 0;
        if (held.is(Blocks.END_PORTAL_FRAME)) return SPECIAL;
        if (held.is(Blocks.ENDER_CHEST)) return CHEST;
        return 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            held.clear();
        }
        if (held.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = held.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Held h = en.getValue();
            if (now - h.since < HOLD_MS) continue;
            it.remove();
            Entity e = mc.level.getEntity(en.getIntKey());
            if (e == null || !e.isAlive()) continue;
            Vec3 real = e.position().add(h.offset);
            e.syncPacketPositionCodec(real.x, real.y, real.z);
            e.moveOrInterpolateTo(real);
        }
    }

    /** Main thread, from EndermanTeleportMixin: true drops the jump to an absolute position. */
    public static boolean dropTeleport(int id, Vec3 target) {
        TeslaMapsConfig c = cfg();
        if (!c.endermanStopTeleport || !active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Entity e = mc.level.getEntity(id);
        if (!(e instanceof EnderMan)) return false;
        Held h = held.get(id);
        Vec3 shown = e.position();
        Vec3 real = h != null ? shown.add(h.offset) : shown;
        if (real.distanceToSqr(target) < 16) {
            if (h != null) h.offset = target.subtract(shown);
            return h != null;
        }
        if (h == null) held.put(id, h = new Held(System.currentTimeMillis()));
        h.offset = target.subtract(shown);
        return true;
    }
}
