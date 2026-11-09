package com.teslamaps.render.gui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface feedback sounds for the mod's own screens. Every theme ships the same ten
 * events under {@code ui_<theme>_<event>}, and the bundled ogg files carry their own
 * relative loudness — a caller only names the event, the config picks the theme and
 * scales all of them together.
 *
 * <p>Played through {@link LoudSound.LoudSoundInstance} so the volume slider can go past
 * the vanilla 1.0 cap, and at the listener so it is never attenuated by distance.
 */
public final class GuiSounds {

    public static final String OFF = "Off";
    public static final String CLICKY = "Clicky";

    /** Order shown in the config dropdown. */
    public static final String[] THEMES = {OFF, CLICKY, "Chime", "Soft", "Digital"};

    private static final Map<String, Identifier> IDS = new HashMap<>();

    private GuiSounds() {}

    private static String theme() {
        String theme = TeslaMapsConfig.get().guiSoundTheme;
        if (theme == null) return OFF;                    // config json predates the field
        for (String t : THEMES) {
            if (t.equalsIgnoreCase(theme)) return t;
        }
        return OFF;
    }

    private static void play(String event, float pitch, float volume) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        String theme = theme();
        if (theme.equals(OFF)) return;

        float vol = volume * config.guiSoundVolume;
        if (vol <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        Identifier id = IDS.computeIfAbsent("ui_" + theme.toLowerCase() + "_" + event,
                key -> Identifier.fromNamespaceAndPath("teslamaps", key));
        mc.getSoundManager().play(new LoudSound.LoudSoundInstance(
                id, SoundSource.MASTER, vol, pitch, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    public static void press() {
        play("click", 1.0f, 2.2f);
    }

    public static void select() {
        play("select", 1.0f, 2.2f);
    }

    public static void toggle(boolean on) {
        play(on ? "toggle_on" : "toggle_off", 1.0f, 2.2f);
    }

    public static void fold(boolean open) {
        play(open ? "expand" : "collapse", 1.0f, 2.0f);
    }

    /** Ratchet tick for a slider passing a step; pitch rises with the value. */
    public static void step(float ratio) {
        play("tick", 0.92f + Math.max(0f, Math.min(1f, ratio)) * 0.3f, 1.4f);
    }

    public static void open() {
        play("open", 1.0f, 2.0f);
    }

    public static void close() {
        play("close", 1.0f, 2.0f);
    }
}
