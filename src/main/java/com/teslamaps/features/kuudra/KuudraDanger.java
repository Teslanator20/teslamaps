package com.teslamaps.features.kuudra;

// Ported from Skyblocker's DangerWarning.java, "JUMP!" subtitle from IQ
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class KuudraDanger {

    private KuudraDanger() {}

    private static int ticks;
    private static int shownColor = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraDanger").section("Kuudra").when(() -> cfg().kuuDangerWarning)
                .tick(KuudraDanger::tick)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Boss Fight");
                    b.toggle("Danger Warning", () -> c.kuuDangerWarning, v -> c.kuuDangerWarning = v)
                            .desc("\"DANGER\" title in the stage colour when the floor under you is about to break");
                    b.toggle("  Sound", () -> c.kuuDangerSound, v -> c.kuuDangerSound = v);
                });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int color = 0;
        if (KuudraBuild.inKuudra()) {
            BlockPos base = mc.player.blockPosition();
            for (int i = 1; i <= 5 && color == 0; i++) color = colorOf(mc.level.getBlockState(base.below(i)).getBlock());
        }
        if (color != 0) {
            if (color != shownColor && cfg().kuuDangerSound) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
            mc.gui.setTimes(0, 10, 5);
            mc.gui.setTitle(Component.literal("DANGER").withColor(color));
            mc.gui.setSubtitle(Component.literal("§c§lJUMP!"));
            shownColor = color;
        } else if (shownColor != 0) {
            shownColor = 0;
            mc.gui.clearTitles();
            mc.gui.resetTitleTimes();
        }
    }

    private static int colorOf(Block b) {
        if (b == Blocks.GREEN_TERRACOTTA) return 0x5E7C16;
        if (b == Blocks.LIME_TERRACOTTA) return 0x80C71F;
        if (b == Blocks.YELLOW_TERRACOTTA) return 0xFED83D;
        if (b == Blocks.ORANGE_TERRACOTTA) return 0xF9801D;
        if (b == Blocks.PINK_TERRACOTTA) return 0xF38BAA;
        if (b == Blocks.RED_TERRACOTTA) return 0xB02E26;
        return 0;
    }
}
