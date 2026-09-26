package com.teslamaps.features.rift;

// Ported from SkyHanni's rift/area/stillgorechateau/RiftBloodEffigies.kt, positions from Skyblocker's EffigyWaypoints.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BloodEffigies {

    private static final BlockPos[] POS = {
            new BlockPos(150, 79, 95), new BlockPos(193, 93, 119), new BlockPos(235, 110, 147),
            new BlockPos(293, 96, 134), new BlockPos(262, 99, 94), new BlockPos(240, 129, 118)};
    private static final AABB[] BOX = new AABB[6];
    private static final Vec3[] LABEL = new Vec3[6];
    static {
        for (int i = 0; i < 6; i++) {
            BOX[i] = new AABB(POS[i]);
            LABEL[i] = Vec3.atCenterOf(POS[i]).add(0, 1.2, 0);
        }
    }

    private static final Pattern RESPAWN = Pattern.compile("Respawn (.+) \\(or click!\\)");

    private static final int UNKNOWN = 0, STANDING = 1, BROKEN = 2;
    private static final int[] state = new int[6];
    private static final long[] respawnAt = new long[6];
    private static final String[] label = new String[6];
    private static final int[] color = new int[6];
    private static boolean active;
    private static int ticks;
    private static Object lastLevel;

    private BloodEffigies() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("BloodEffigies").section("Rift").when(() -> cfg().riftEffigies)
                .tick(BloodEffigies::tick)
                .world(BloodEffigies::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Blood Effigies", () -> c.riftEffigies, v -> c.riftEffigies = v)
                            .desc("Stillgore Château: red \"Break Effigy #n!\" on standing effigies (from the scoreboard), yellow countdown when one respawns soon.");
                    b.slider("  Respawning Soon (min)", 1f, 15f, () -> (float) c.riftEffigiesSoonMin, v -> c.riftEffigiesSoonMin = Math.round(v));
                    b.toggle("  Show Unknown", () -> c.riftEffigiesUnknown, v -> c.riftEffigiesUnknown = v)
                            .desc("Grey marker when the state or respawn time is not known yet.");
                    b.toggle("  Chat Message", () -> c.riftEffigiesChat, v -> c.riftEffigiesChat = v)
                            .desc("\"Effigy #n broken/respawned\" in chat.");
                });
    }

    private static void reset() {
        Arrays.fill(state, UNKNOWN);
        Arrays.fill(respawnAt, 0);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        active = RiftZone.inStillgore();
        if (!active || ticks++ % 10 != 0) return;
        readScoreboard(mc);
        if (ticks % 20 == 1) readStands(mc);
        buildLabels(mc);
    }

    private static void readScoreboard(Minecraft mc) {
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return;
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            if (!sb.listPlayerScores(holder).containsKey(obj)) continue;
            PlayerTeam team = sb.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;
            String line = ChatText.formatted(team.getPlayerPrefix()) + ChatText.formatted(team.getPlayerSuffix());
            int at = line.indexOf("Effigies:");
            if (at < 0) continue;
            char col = 'f';
            int idx = 0;
            for (int i = at + 9; i < line.length() && idx < 6; i++) {
                char ch = line.charAt(i);
                if (ch == '§' && i + 1 < line.length()) {
                    char c = line.charAt(++i);
                    if ("0123456789abcdef".indexOf(c) >= 0) col = c;
                } else if (ch == '⧯') {
                    update(idx++, col == '7' ? STANDING : col == 'c' ? BROKEN : UNKNOWN);
                }
            }
            return;
        }
    }

    private static void update(int i, int now) {
        if (now == UNKNOWN) return;
        int old = state[i];
        state[i] = now;
        if (old == BROKEN && now == STANDING) {
            chat("§aEffigy #" + (i + 1) + " respawned!");
        } else if (old == STANDING && now == BROKEN) {
            chat("§cEffigy #" + (i + 1) + " broken!");
            respawnAt[i] = System.currentTimeMillis() + 20 * 60_000L;
        }
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (cfg().riftEffigiesChat && mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + msg));
    }

    private static void readStands(Minecraft mc) {
        if (mc.player == null) return;
        AABB area = mc.player.getBoundingBox().inflate(15);
        for (Entity e : mc.level.getEntities((Entity) null, area, x -> x instanceof ArmorStand && x.hasCustomName())) {
            String name = StringUtil.stripTrim(e.getCustomName().getString());
            int i = nearest(e.position());
            if (i < 0) continue;
            Matcher m = RESPAWN.matcher(name);
            if (m.find()) {
                int sec = parseTime(m.group(1));
                if (sec >= 0) {
                    state[i] = BROKEN;
                    respawnAt[i] = System.currentTimeMillis() + sec * 1000L;
                }
            } else if (name.equals("Break it!")) {
                state[i] = STANDING;
            }
        }
    }

    private static int nearest(Vec3 p) {
        int best = -1;
        double bd = 36;
        for (int i = 0; i < 6; i++) {
            double d = Vec3.atCenterOf(POS[i]).distanceToSqr(p.x, POS[i].getY() + 0.5, p.z);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    // "14m59s" / "1s"
    private static int parseTime(String s) {
        int total = 0, num = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) num = num * 10 + (ch - '0');
            else if (ch == 'h' || ch == 'm' || ch == 's') {
                total += ch == 'h' ? num * 3600 : ch == 'm' ? num * 60 : num;
                num = 0;
                any = true;
            }
        }
        return any ? total : -1;
    }

    private static void buildLabels(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            String name = "Effigy #" + (i + 1);
            label[i] = null;
            switch (state[i]) {
                case STANDING -> { label[i] = "§cBreak " + name + "!"; color[i] = 0x80FF5555; }
                case BROKEN -> {
                    if (respawnAt[i] == 0) {
                        if (c.riftEffigiesUnknown) { label[i] = "§7Unknown Time (" + name + ")"; color[i] = 0x80AAAAAA; }
                    } else {
                        long left = Math.max(0, respawnAt[i] - now);
                        if (left < c.riftEffigiesSoonMin * 60_000L) {
                            label[i] = "§e" + name + " respawning in §b" + RiftTimer.fmt((int) (left / 1000));
                            color[i] = 0x80FFFF55;
                        }
                    }
                }
                default -> { if (c.riftEffigiesUnknown) { label[i] = "§7Unknown State (" + name + ")"; color[i] = 0x80AAAAAA; } }
            }
            if (label[i] == null && mc.player != null && mc.player.position().distanceToSqr(Vec3.atCenterOf(POS[i])) <= 225) {
                label[i] = "§7" + name;
                color[i] = 0;
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!active) return;
        for (int i = 0; i < 6; i++) {
            String l = label[i];
            if (l == null) continue;
            if (color[i] != 0) ESPRenderer.drawFilledBox(pose, BOX[i], color[i], cam, true);
            ESPRenderer.drawText(pose, l, LABEL[i], 1.5f, cam);
        }
    }
}
