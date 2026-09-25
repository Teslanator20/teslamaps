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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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

    public static void register() {
        Feature.of("PartyMemberGlow").section("ESP").legit()
                .when(() -> TeslaMapsConfig.get().highlightTeammates && TeslaMapsConfig.get().highlightPartyOutside)
                .tick(HighlightTeammates::tickParty);
    }

    // SkyHanni PartyMemberOutlines: glow party members outside dungeons
    private static void tickParty() {
        if (DungeonManager.isInDungeon() || !PartyTracker.inParty() || !SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().highlightPartyColor) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (PartyTracker.isMember(p.getGameProfile().name())) EntityGlow.mark(p, color);
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().highlightTeammates || !DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            var dp = PlayerTracker.getPlayer(p.getName().getString());
            if (dp.isEmpty()) continue;
            int color = classColor(dp.get().getDungeonClass());
            ESPRenderer.drawBoxOutline(matrices, ESPRenderer.lerpBox(p), color, 3.0f, cameraPos, true);
        }
    }
}
