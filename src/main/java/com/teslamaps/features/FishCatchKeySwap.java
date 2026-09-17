package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After a rare catch, attack/use move to O/I for a moment so a stray click can't do anything. */
public final class FishCatchKeySwap {

    public static final String TARGET = "Drowned Captain";

    private static final Pattern PARTY_CATCH = Pattern.compile(
            "Party > (?:\\[[^\\]]+\\] )?(\\w+): (?:DOUBLE HOOK[:!] )?(?:I caught an? (.+)!|An? (.+) has spawned!)(?: \\(x?\\d+x?\\))?");

    private static volatile boolean active = false;
    private static long windowEnd = 0;
    private static boolean attackConsumed = false;
    private static boolean useConsumed = false;

    private FishCatchKeySwap() {}

    public static void trigger() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.rareCatchKeySwap || c.rareCatchKeySwapSeconds <= 0f) return;
        windowEnd = System.currentTimeMillis() + (long) (c.rareCatchKeySwapSeconds * 1000f);
        attackConsumed = keyHeld(GLFW.GLFW_KEY_O);
        useConsumed = keyHeld(GLFW.GLFW_KEY_I);
        active = true;
    }

    public static void onChatMessage(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.rareCatchKeySwap || !c.rareCatchKeySwapAll) return;
        Matcher m = PARTY_CATCH.matcher(StringUtil.stripTrim(text));
        String creature = m.matches() ? (m.group(2) != null ? m.group(2) : m.group(3)) : null;
        if (!TARGET.equals(creature)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && m.group(1).equals(mc.player.getName().getString())) return;
        trigger();
    }

    public static void overrideIsDown(KeyMapping mapping, CallbackInfoReturnable<Boolean> cir) {
        if (!isActive()) return;
        Options o = Minecraft.getInstance().options;
        if (mapping == o.keyAttack) cir.setReturnValue(keyHeld(GLFW.GLFW_KEY_O));
        else if (mapping == o.keyUse) cir.setReturnValue(keyHeld(GLFW.GLFW_KEY_I));
    }

    /** true = handled, mixin zeroes clickCount so accidental clicks don't fire after restore. */
    public static boolean overrideConsumeClick(KeyMapping mapping, CallbackInfoReturnable<Boolean> cir) {
        if (!isActive()) return false;
        Options o = Minecraft.getInstance().options;
        if (mapping == o.keyAttack) {
            boolean held = keyHeld(GLFW.GLFW_KEY_O);
            boolean click = held && !attackConsumed;
            attackConsumed = held;
            cir.setReturnValue(click);
            return true;
        }
        if (mapping == o.keyUse) {
            boolean held = keyHeld(GLFW.GLFW_KEY_I);
            boolean click = held && !useConsumed;
            useConsumed = held;
            cir.setReturnValue(click);
            return true;
        }
        return false;
    }

    private static boolean isActive() {
        if (!active) return false;
        if (System.currentTimeMillis() >= windowEnd) {
            active = false;
            Options o = Minecraft.getInstance().options;
            o.keyAttack.setDown(false);
            o.keyUse.setDown(false);
            return false;
        }
        return true;
    }

    private static boolean keyHeld(int key) {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }
}
