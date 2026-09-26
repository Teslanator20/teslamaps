package com.teslamaps.safari;

// Ported from GanKura's EntityHighlightManager (showSafariFish)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ScrappyFish {

    private static final List<Entity> fish = new ArrayList<>();
    private static int scanTicks = 0;

    private ScrappyFish() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScrappyFish").section("Critter Safari").when(() -> cfg().safariScrappyFish)
                .tick(ScrappyFish::tick)
                .world(ScrappyFish::render);
    }

    private static boolean active() {
        if (!SafariData.inSafari()) return false;
        if (!cfg().safariScrappyFishStopCaught) return true;
        SafariData.Critter scrappy = SafariData.byName("Scrappy");
        return scrappy == null || !SafariTracker.hasCaught(scrappy);
    }

    private static void tick() {
        if (!active()) {
            fish.clear();
            return;
        }
        if (++scanTicks >= 10) {
            scanTicks = 0;
            scan();
        }
        fish.removeIf(Entity::isRemoved);
        EspStyle style = EspStyle.of(cfg().safariScrappyFishStyle);
        for (Entity e : fish) style.tickEntity(e);
    }

    private static void scan() {
        fish.clear();
        List<Entity> labels = new ArrayList<>();
        List<Entity> all = EntityCache.all();
        for (Entity e : all) {
            if (!e.hasCustomName() || e instanceof AbstractFish) continue;
            String n = StringUtil.strip(e.getCustomName().getString()).trim();
            if (n.regionMatches(true, 0, "Sparkling ", 0, 10)) n = n.substring(10);
            if (SafariData.byName(n) != null) labels.add(e);
        }
        for (Entity e : all) {
            if (!(e instanceof AbstractFish) || e.isInvisible() || e.hasCustomName()) continue;
            if (critterBody(e, labels)) continue;
            fish.add(e);
        }
    }

    // a fish right under a critter label is that critter, not food
    private static boolean critterBody(Entity e, List<Entity> labels) {
        for (Entity l : labels) {
            if (l.position().distanceToSqr(e.position()) < 9.0) return true;
        }
        return false;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (fish.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().safariScrappyFishStyle);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity e : fish) if (!e.isRemoved()) style.drawEntity(pose, e, cam, pt);
    }
}
