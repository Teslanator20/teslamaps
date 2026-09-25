package com.teslamaps.features;

// Ported from SkyOcean's hidearmour mixins (HumanoidArmorLayer / CustomHeadLayer / SkullBlockRenderer / EquipmentLayerRenderer)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Armor and helmet alpha for players; NPCs (uuid v2) stay untouched. */
public final class TransparentArmor {

    /** Alpha of the armor layer being submitted right now, 255 = vanilla. */
    public static int armor = 255;
    /** Alpha of the head layer being submitted right now, 255 = vanilla. */
    public static int head = 255;

    private TransparentArmor() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TransparentArmor")
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Player Armor");
                    b.toggle("Transparent Armor", () -> c.transparentArmor, v -> c.transparentArmor = v)
                            .desc("Makes player armor and helmets see-through so skins and capes show. NPCs are not affected.");
                    b.slider("  Own Armor Opacity %", 0f, 100f, () -> c.transparentArmorSelf, v -> c.transparentArmorSelf = v)
                            .desc("0 = your armor is hidden completely, 100 = normal.");
                    b.slider("  Other Players Opacity %", 0f, 100f, () -> c.transparentArmorOthers, v -> c.transparentArmorOthers = v)
                            .desc("0 = hidden completely, 100 = normal.");
                });
    }

    public static int alphaFor(EntityRenderState state) {
        TeslaMapsConfig c = cfg();
        if (!c.transparentArmor || !(state instanceof AvatarRenderState a)) return 255;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 255;
        float pct;
        if (a.id == mc.player.getId()) {
            pct = c.transparentArmorSelf;
        } else {
            Entity e = mc.level.getEntity(a.id);
            if (!(e instanceof Player p) || p.getUUID().version() == 2) return 255;
            pct = c.transparentArmorOthers;
        }
        if (pct >= 100f) return 255;
        return Math.max(0, Math.min(255, Math.round(pct * 2.55f)));
    }

    public static int apply(int alpha, int argb) {
        int a = Math.min((argb >>> 24) & 0xFF, alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
