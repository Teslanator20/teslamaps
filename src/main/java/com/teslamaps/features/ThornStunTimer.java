package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;

public class ThornStunTimer {
    public static final String SAMPLE_TEXT = "Thorn Stun: Not Stunned"; // widest state, for HUD-editor sizing/preview

    private static final BlockPos ARENA_POS = new BlockPos(7, 77, 34); // bear watch pos, used for arena proximity
    private static final int STUN_DURATION = 82; // ~4.1s, Thorn's stun length

    private static int stunTicks = -1; // -1 = not stunned, >0 = ticks left

    public static void tick() {
        if (!isInThornArena()) {
            stunTicks = -1;
            return;
        }
        if (stunTicks > -1) stunTicks--;
    }

    // called from ClientPlayNetworkHandlerMixin on every hurt animation
    public static void onHurtAnimation(int entityId) {
        if (!TeslaMapsConfig.get().thornStunTimer) return;
        if (!isInThornArena()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity instanceof Ghast) {
            stunTicks = STUN_DURATION;
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().thornStunTimer) return;
        if (!isInThornArena()) return;

        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        if (TeslaMapsConfig.get().thornStunHealerOnly && !isHealer()) return;

        String text = stunTicks >= 0
            ? String.format("§5Thorn Stun: §5%.2fs", stunTicks / 20f)
            : "§5Thorn Stun: §cNot Stunned";

        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, mc, config.thornStunX, config.thornStunY, config.thornStunScale, text);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, "§5Thorn Stun: §cNot Stunned");
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static boolean isInThornArena() {
        if (!DungeonManager.isInDungeon()) return false;
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor.getLevel() != 4) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        double dx = mc.player.getX() - (ARENA_POS.getX() + 0.5);
        double dz = mc.player.getZ() - (ARENA_POS.getZ() + 0.5);
        return dx * dx + dz * dz <= 200 * 200;
    }

    private static boolean isHealer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String playerName = mc.player.getName().getString();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(playerName)) {
                return "Healer".equals(dp.getDungeonClass());
            }
        }
        return false;
    }
}
