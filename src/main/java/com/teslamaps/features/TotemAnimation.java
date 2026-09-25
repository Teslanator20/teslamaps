package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.regex.Pattern;

// Ported from SkyOcean's InvulnerabilityAnimation.kt
public final class TotemAnimation {

    private static final Pattern BONZO = Pattern.compile("Your (?:⚚ )?Bonzo's Mask saved your life!");
    private static final Pattern PHOENIX = Pattern.compile("Your Phoenix Pet saved you from certain death!");
    private static final Pattern SPIRIT = Pattern.compile("Second Wind Activated! Your Spirit Mask saved your life!");
    private static final Pattern REMNANT = Pattern.compile("Your Remnant of the Eye saved you from certain death!");

    private TotemAnimation() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TotemAnimation").section("Timers").when(() -> cfg().totemAnimation)
                .serverChat(TotemAnimation::onChat)
                .config("Timers", b -> b.toggle("Totem Animation on Procs", () -> cfg().totemAnimation, v -> cfg().totemAnimation = v)
                        .desc("Totem particles, sound and item pop when Bonzo/Spirit Mask, Phoenix or Remnant of the Eye save you"));
    }

    private static void onChat(String raw) {
        if (raw.indexOf("saved") < 0) return;
        String t = StringUtil.strip(raw).trim();
        int which;
        if (BONZO.matcher(t).matches()) which = 0;
        else if (SPIRIT.matcher(t).matches()) which = 1;
        else if (PHOENIX.matcher(t).matches()) which = 2;
        else if (REMNANT.matcher(t).matches()) which = 3;
        else return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack icon = which < 3 ? TimerTriggers.maskIcon(which) : null;
        if (icon == null) icon = new ItemStack(which == 3 ? Items.ENDER_EYE : Items.TOTEM_OF_UNDYING);
        mc.particleEngine.createTrackingEmitter(mc.player, ParticleTypes.TOTEM_OF_UNDYING, 30);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.TOTEM_USE,
                mc.player.getSoundSource(), 1f, 1f, false);
        mc.gameRenderer.displayItemActivation(icon);
    }
}
