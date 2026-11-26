package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class HighlightTeammates {

    private static int classColor(String cls) {
        return switch (cls) {
            case "Archer" -> 0xFFFFA500;
            case "Berserk" -> 0xFFFF5555;
            case "Healer" -> 0xFFFF55FF;
            case "Mage" -> 0xFF55FFFF;
            case "Tank" -> 0xFF55FF55;
            default -> 0xFFFFFFFF;
        };
    }

    private static final List<Player> mates = new ArrayList<>();

    public static void register() {
        Feature.of("PartyMemberGlow").section("ESP").legit()
                .when(() -> TeslaMapsConfig.get().highlightTeammates && TeslaMapsConfig.get().highlightPartyOutside)
                .tick(HighlightTeammates::tickParty)
                .world(HighlightTeammates::renderNames);
    }

    // SkyHanni PartyMemberOutlines: glow party members outside dungeons
    private static void tickParty() {
        mates.clear();
        if (DungeonManager.isInDungeon() || !PartyTracker.inParty() || !SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().highlightPartyColor) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isRemoved()) continue;
            if (!PartyTracker.isMember(p.getGameProfile().name())) continue;
            EntityGlow.mark(p, color);
            mates.add(p);
        }
    }

    // Sparkling Mutuals TeammateEsp: see-through name over party members
    private static void renderNames(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (mates.isEmpty() || !c.highlightPartyNames) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int color = TeslaMapsConfig.parseColor(c.highlightPartyColor) | 0xFF000000;
        float base = c.highlightPartyNameScale;
        for (Player p : mates) {
            if (p.isRemoved()) continue;
            Vec3 pos = p.getPosition(pt);
            double dist = pos.distanceTo(cam);
            float scale = (float) (base * Math.max(dist * 0.12, 1.0));
            Vec3 at = new Vec3(pos.x, pos.y + p.getBbHeight() + 0.7 + dist * 0.015, pos.z);
            ESPRenderer.drawText(pose, p.getGameProfile().name(), at, scale, cam, false, true, color);
        }
    }

    /** The custom label replaces the vanilla name tag. */
    public static boolean hideNameTag(Entity e) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.highlightPartyNames && !mates.isEmpty() && e instanceof Player && mates.contains(e)
                && c.highlightTeammates && c.highlightPartyOutside && c.section("ESP") && !LegitMode.blocksCheats();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().highlightTeammates || !DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            var dp = PlayerTracker.getPlayer(p.getName().getString());
            if (dp.isEmpty()) continue;
            String cls = dp.get().getDungeonClass();
            int color = com.teslamaps.dungeon.ClassColors.or(cls, classColor(cls));
            ESPRenderer.drawBoxOutline(matrices, ESPRenderer.lerpBox(p), color, 3.0f, cameraPos, true);
        }
    }
}
