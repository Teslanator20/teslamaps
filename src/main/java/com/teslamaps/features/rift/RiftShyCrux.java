package com.teslamaps.features.rift;

// After SkyHanni's rift/area/wyldwoods/ShyCruxWarnings.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.List;
import java.util.Set;

/** A Shy crux within 8 blocks: "Look away!" title. */
public final class RiftShyCrux {

    private static final Set<String> NAMES = Set.of("I'm ugly! :(", "Eek!", "Don't look at me!", "Look away!");
    private static final Component TITLE = Component.literal("§eLook away!");

    private static int ticks;

    private RiftShyCrux() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("RiftShyCrux").section("Rift").when(() -> cfg().riftShyCruxWarning)
                .tick(RiftShyCrux::tick)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Shy Crux Warning", () -> c.riftShyCruxWarning, v -> c.riftShyCruxWarning = v)
                            .desc("Wyld Woods: \"Look away!\" title while a Shy is within 8 blocks.")
                            .tags("rift", "shy", "crux", "look away", "wyld woods");
                });
    }

    private static void tick() {
        if (ticks++ % 2 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.getArea().contains("Rift")) return;
        List<ArmorStand> stands = EntityCache.namedArmorStands();
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand as = stands.get(i);
            if (as.distanceToSqr(mc.player) > 64) continue;
            if (!NAMES.contains(StringUtil.stripTrim(as.getName().getString()))) continue;
            mc.gui.setTimes(0, 4, 0);
            mc.gui.setTitle(TITLE);
            return;
        }
    }
}
