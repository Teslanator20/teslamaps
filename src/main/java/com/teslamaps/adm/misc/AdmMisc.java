package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class AdmMisc {
    public static final String PREFIX = "§8[§6ADM§8] ";

    private AdmMisc() {}

    public static void register() {
        AdmCore.register();
        Witherborn.register();
        RandomStuff.register();
        I4Complete.register();
        com.teslamaps.adm.misc.pf.HubNametags.register();
        AgroSphere.register();
        Timers.register();
        LcmMeleeWarning.register();
    }

    static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void msg(String text) {
        chat(PREFIX + text);
    }

    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal(text)));
    }

    /** Odin's alert: a title for a second, with a pling. */
    public static void alert(String title, boolean sound) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 20, 5);
        mc.gui.hud.setTitle(Component.literal(title));
        if (sound && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    public static String strip(String s) {
        return com.teslamaps.utils.StringUtil.strip(s).trim();
    }

    /** Server ticks since the last call, for code that runs once per server tick. */
    public static final class TickPump {
        private long last = -1;

        public int poll() {
            long now = ServerTickTimer.now();
            if (last < 0 || now < last) { last = now; return 0; }
            int n = (int) Math.min(40, now - last);
            last = now;
            return n;
        }
    }

    static boolean on(boolean toggle) {
        return toggle && Adm.on();
    }
}
