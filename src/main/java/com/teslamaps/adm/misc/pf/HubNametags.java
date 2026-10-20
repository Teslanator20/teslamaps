package com.teslamaps.adm.misc.pf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.misc.RandomStuff;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;

// Ported from ADM's pf/HubNametags.kt: Dungeon Hub stat line over real players' nametags
public final class HubNametags {
    private static final double RANGE_SQ = 64.0 * 64.0;
    private static final double SCORE_RANGE_SQ = 10.0 * 10.0;
    private static final double LINE = 9 * 1.15 * 0.025;

    private static long hubCheckedAt;
    private static boolean hubCached;

    private HubNametags() {}

    public static void register() {
        Feature.of("AdmHubNametags").section(Adm.SECTION).when(RandomStuff::showsHubNametags)
                .world(HubNametags::render);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inDungeonHub()) return;
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var me = mc.player;
        if (level == null || me == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String floor = c.admPbFloor == null ? "F7" : c.admPbFloor;
        String mode = floor.startsWith("M") ? "m" : "f";
        String num = floor.equals("E") ? "0" : floor.substring(1);
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean belowName = level.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null;
        for (Player p : level.players()) {
            if (p == me || !p.isAlive() || p.isInvisible()) continue;
            if (p.getUUID().version() != 4) continue;
            Vec3 pos = p.getPosition(pt);
            if (pos.distanceToSqr(cam) > RANGE_SQ) continue;
            Vec3 att = p.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, p.getYRot(pt));
            if (att == null) continue;
            double nameTop = att.y + 0.5 + (belowName && pos.distanceToSqr(cam) < SCORE_RANGE_SQ ? LINE : 0.0);
            String line = lineFor(p.getName().getString(), mode, num);
            ESPRenderer.drawText(pose, line, pos.add(0, nameTop + LINE + c.admNametagGap, 0), 1f, cam, false, true);
        }
    }

    private static boolean inDungeonHub() {
        long now = System.currentTimeMillis();
        if (now - hubCheckedAt < 500) return hubCached;
        hubCheckedAt = now;
        hubCached = false;
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return false;
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            if (info.getTabListDisplayName() == null) continue;
            String s = info.getTabListDisplayName().getString().trim();
            if (s.startsWith("Area:") && s.contains("Dungeon Hub")) { hubCached = true; break; }
        }
        return hubCached;
    }

    private static String lineFor(String name, String mode, String floor) {
        PlayerStats.Lookup entry = PlayerStats.lookup(name);
        if (entry instanceof PlayerStats.Ready r) {
            return "§e" + PlayerStats.cataStr(r.stats()) + " §8| §b" + PlayerStats.secretsStr(r.stats().secrets)
                    + " §8| §d" + PlayerStats.pbStr(r.stats(), mode, floor);
        }
        if (entry instanceof PlayerStats.Failed) return "§c?";
        return "§7…";
    }
}
