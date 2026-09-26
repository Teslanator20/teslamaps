package com.teslamaps.features;

// Ported from Skytils' MayorDiana (trackGaiaHits)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class GaiaConstructHits {

    private static final Int2IntOpenHashMap hits = new Int2IntOpenHashMap();
    private static final List<Label> labels = new ArrayList<>();
    private static Feature feature;

    private record Label(Entity golem, String text) {}

    private GaiaConstructHits() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GaiaConstructHits").section("Combat").when(() -> cfg().gaiaConstructHits)
                .tick(GaiaConstructHits::tick)
                .world(GaiaConstructHits::render)
                .config("Combat", b -> b.toggle("Gaia Construct Hit Counter", () -> cfg().gaiaConstructHits, v -> cfg().gaiaConstructHits = v)
                        .desc("Shows \"Hits: x / needed\" above Gaia Constructs (Diana). Red until the phase has enough hits, then green")
                        .tags("diana", "mythological", "golem", "gaia"));
    }

    /** handleSoundEvent, main thread. */
    public static void onSound(ClientboundSoundPacket packet) {
        if (feature == null || !feature.active() || !SkyblockUtils.isInSkyblock()) return;
        if (packet.getVolume() != 0.8f || packet.getSound().value() != SoundEvents.ANVIL_LAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 at = new Vec3(Math.floor(packet.getX()), Math.floor(packet.getY()), Math.floor(packet.getZ()));
        IronGolem best = null;
        double bestD = 25 * 25;
        for (IronGolem g : mc.level.getEntitiesOfClass(IronGolem.class, new AABB(at, at).inflate(25))) {
            if (g.getHealth() <= 0) continue;
            double d = g.distanceToSqr(at);
            if (d <= bestD) { bestD = d; best = g; }
        }
        if (best != null) hits.addTo(best.getId(), 1);
    }

    private static void tick() {
        labels.clear();
        Minecraft mc = Minecraft.getInstance();
        if (hits.isEmpty() || mc.level == null) return;
        var it = hits.int2IntEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!(mc.level.getEntity(e.getIntKey()) instanceof IronGolem g) || !g.isAlive()) {
                it.remove();
                continue;
            }
            if (g.hurtTime == 10) e.setValue(0);
            double max = g.getAttributeBaseValue(Attributes.MAX_HEALTH);
            double pct = max > 0 ? g.getHealth() / max : 1;
            int needed = pct <= 0.33 ? 7 : pct <= 0.66 ? 6 : 5;
            int n = e.getIntValue();
            labels.add(new Label(g, (n < needed ? "§c" : "§a") + "Hits: " + n + " / " + needed));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (int i = 0, n = labels.size(); i < n; i++) {
            Label l = labels.get(i);
            ESPRenderer.drawText(pose, l.text(), l.golem().position().add(0, 3, 0), 1.2f, cam);
        }
    }
}
