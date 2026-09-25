package com.teslamaps.features;

// Odin's SwapSound: pickaxe left click while the server still holds a non-pickaxe slot
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.MultiPlayerGameModeAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

public class SwapSound {

    private static int lastTick = -1;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SwapSound").section("Sounds").when(() -> cfg().swapSound)
                .config("Sounds", b -> {
                    b.toggle("Swap Sound (Stonk Swap)", () -> cfg().swapSound, v -> cfg().swapSound = v)
                            .desc("Plays when you left click with a pickaxe the server does not know you hold yet");
                    b.toggle("  Only on Blocks", () -> cfg().swapSoundOnlyBlock, v -> cfg().swapSoundOnlyBlock = v);
                    b.soundDropdown("  Sound", SoundOptions.keys(), () -> cfg().swapSoundType, v -> cfg().swapSoundType = v);
                    b.slider("  Volume", 0f, 4f, () -> cfg().swapSoundVolume, v -> cfg().swapSoundVolume = v, 2);
                    b.slider("  Pitch", 0.5f, 2f, () -> cfg().swapSoundPitch, v -> cfg().swapSoundPitch = v, 2);
                    b.button("  Test Sound", SwapSound::play);
                });
    }

    public static void onLeftClick() {
        TeslaMapsConfig c = cfg();
        if (!c.swapSound || !c.section("Sounds")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (c.swapSoundOnlyBlock && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)) return;
        if (!mc.player.getMainHandItem().is(ItemTags.PICKAXES)) return;
        int reported = ((MultiPlayerGameModeAccessor) mc.gameMode).teslamaps$carriedIndex();
        if (reported == mc.player.getInventory().getSelectedSlot()) return;
        ItemStack last = mc.player.getInventory().getItem(reported);
        if (last.is(ItemTags.PICKAXES)) return;
        if (mc.player.tickCount == lastTick) return;
        lastTick = mc.player.tickCount;
        play();
    }

    private static void play() {
        TeslaMapsConfig c = cfg();
        LoudSound.play(SoundOptions.resolve(c.swapSoundType), c.swapSoundVolume, c.swapSoundPitch);
    }
}
