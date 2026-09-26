package com.teslamaps.features;

// Concept from Oringo Client's ArachneQOL / ArachneMixin
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class ArachneQol {

    private static volatile boolean present;
    private static int ticks;

    private ArachneQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ArachneQol").section("Combat").when(() -> cfg().arachneQol)
                .tick(ArachneQol::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Arachne");
                    b.toggle("Arachne QOL", () -> c.arachneQol, v -> c.arachneQol = v)
                            .desc("Spider's Den, only while Arachne is loaded").tags("spider", "arachne");
                    b.toggle("  Hide Other Players", () -> c.arachneHidePlayers, v -> c.arachneHidePlayers = v);
                    b.toggle("  Click Through Cobwebs", () -> c.arachneWebClick, v -> c.arachneWebClick = v)
                            .cheat().desc("Cobwebs can't be targeted, so hits go through them onto Arachne");
                    ArachneTimers.config(b);
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        boolean found = false;
        if (SkyblockUtils.isInArea("Spider's Den")) {
            for (ArmorStand s : EntityCache.namedArmorStands()) {
                String n = s.getName().getString();
                if (n.contains("Arachne ") && n.contains("❤") && !n.contains("Arachne's")) {
                    found = true;
                    break;
                }
            }
        }
        present = found;
    }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return present && c.arachneQol && c.section("Combat");
    }

    public static boolean hidePlayers() {
        return present && cfg().arachneHidePlayers && on();
    }

    /** ClipContextMixin: cobwebs have no outline while this is true. */
    public static boolean webClickThrough() {
        return present && cfg().arachneWebClick && on() && !com.teslamaps.features.LegitMode.blocksCheats();
    }
}
