package com.teslamaps.features.events;

import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Shared carnival state: area, perk, SkyBlock profile, block updates for the minigames. */
public final class Carnival {

    public static final String CATEGORY = "Events";

    private static boolean inArea;
    private static String profile = "default";
    private static int ticks;

    private Carnival() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category(CATEGORY, "Skyblock", true);
        Feature.of("Carnival").section(CATEGORY)
                .when(Carnival::anyOn)
                .tick(Carnival::tick)
                .serverChat(Carnival::onChat);
    }

    private static boolean anyOn() {
        TeslaMapsConfig c = cfg();
        return c.carnivalZombieShootout || c.carnivalCatchAFish || c.carnivalShopHelper || c.carnivalGoals
                || c.carnivalTicketReminder || c.carnivalQuickStart || c.carnivalFruitDigging;
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        inArea = SkyblockUtils.isInArea("Hub") && FishingHelpers.subArea().contains("Carnival")
                && (!ElectionApi.isLoaded() || perkActive());
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Profile ID:") < 0) return;
        String s = StringUtil.stripTrim(raw);
        if (s.startsWith("Profile ID: ")) profile = s.substring(12).trim();
    }

    public static boolean perkActive() {
        return ElectionApi.hasPerk("Chivalrous Carnival");
    }

    public static boolean inArea() {
        return inArea && cfg().section(CATEGORY);
    }

    public static String profile() {
        return profile;
    }

    public static boolean listening() {
        return inArea && (cfg().carnivalZombieShootout || cfg().carnivalFruitDigging);
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!cfg().section(CATEGORY)) return;
        if (cfg().carnivalZombieShootout) CarnivalShootout.onBlockUpdate(pos, oldState, newState);
        if (cfg().carnivalFruitDigging) FruitDigging.onBlockUpdate(pos, oldState, newState);
    }
}
