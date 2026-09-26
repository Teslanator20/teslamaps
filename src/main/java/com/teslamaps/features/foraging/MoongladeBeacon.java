package com.teslamaps.features.foraging;

// Ported from SkyOcean's MoongladeBeacon
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;

import java.util.List;

/** Tints the real Moonglade beacon beam red (inactive) or green (active) from the tab widget. */
public final class MoongladeBeacon {

    private static final BlockPos POS = new BlockPos(-688, 128, 65);
    private static final List<BeaconBeamOwner.Section> RED = List.of(new BeaconBeamOwner.Section(0x20FF5555));
    private static final List<BeaconBeamOwner.Section> GREEN = List.of(new BeaconBeamOwner.Section(0x2055FF55));

    private static volatile List<BeaconBeamOwner.Section> sections;
    private static int ticks;

    private MoongladeBeacon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MoongladeBeacon").section("Foraging").when(() -> cfg().moongladeBeaconColor)
                .tick(MoongladeBeacon::tick)
                .config("Foraging", b -> b.toggle("Moonglade Beacon Color", () -> cfg().moongladeBeaconColor,
                                v -> cfg().moongladeBeaconColor = v)
                        .desc("Beacon beam in Moonglade turns red while the beacon is inactive, green while active (tab widget)")
                        .tags("moonglade", "beacon"));
    }

    private static void tick() {
        if (ticks++ % 20 != 0) return;
        if (!Foraging.inMoonglade()) {
            sections = null;
            return;
        }
        List<String> lines = TabListUtils.getOrderedLines();
        boolean inWidget = false, found = false, available = false;
        for (String raw : lines) {
            String l = StringUtil.strip(raw);
            if (l.trim().startsWith("Moonglade Beacon")) {
                inWidget = found = true;
                continue;
            }
            if (!inWidget) continue;
            if (l.isBlank() || !l.startsWith(" ")) break;
            if (l.contains("Cooldown: AVAILABLE")) available = true;
        }
        sections = !found ? null : available ? RED : GREEN;
    }

    /** BeaconBlockEntity.getBeamSections, null = vanilla. */
    public static List<BeaconBeamOwner.Section> sections(BlockPos pos) {
        List<BeaconBeamOwner.Section> s = sections;
        if (s == null || !POS.equals(pos) || !cfg().moongladeBeaconColor || !cfg().section("Foraging")) return null;
        return s;
    }
}
