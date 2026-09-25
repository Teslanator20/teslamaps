package com.teslamaps.features;

// Ported from Skytils' mixins/hooks/renderer/RenderHook.kt (lowerEndermanNametags)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** Enderman / Zealot / Voidgloom health tags sit lower and a bit closer, for the Zealot grind. */
public final class LowerEndermanNametags {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;

    private LowerEndermanNametags() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("LowerEndermanNametags").section("Render").when(() -> cfg().lowerEndermanNametags)
                .tick(LowerEndermanNametags::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Name Tags");
                    b.toggle("Lower Enderman Nametags", () -> c.lowerEndermanNametags, v -> c.lowerEndermanNametags = v)
                            .desc("Enderman / Zealot / Voidling / Voidgloom health tags drawn lower, at body height.");
                });
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        IntOpenHashSet next = new IntOpenHashSet();
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            Component n = as.getCustomName();
            if (n != null && matches(StringUtil.strip(n.getString()))) next.add(as.getId());
        }
        ids = next;
    }

    private static boolean matches(String s) {
        boolean tag = s.contains("❤") || s.endsWith(" Hit") || s.endsWith(" Hits");
        return tag && (s.contains("Enderman") || s.contains("Zealot") || s.contains("Voidling") || s.contains("Voidgloom"));
    }

    /** From EntityNametagMixin at the end of EntityRenderer.extractRenderState. */
    public static void adjust(Entity e, EntityRenderState state) {
        if (ids.isEmpty() || state.nameTagAttachment == null || !cfg().lowerEndermanNametags
                || !ids.contains(e.getId()) || !cfg().section("Render")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dx = state.x - mc.player.getX(), dz = state.z - mc.player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-4) { dx /= len; dz /= len; } else { dx = 0; dz = 0; }
        Vec3 a = state.nameTagAttachment;
        state.nameTagAttachment = new Vec3(a.x - dx, a.y - 1.5, a.z - dz);
    }
}
