package com.teslamaps.features;

// Ported from Odin's TerminalSounds.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class TerminalSounds {

    private static long lastPlayed = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TerminalSounds").section("Puzzles & Terminals")
                .when(() -> cfg().terminalSounds && cfg().terminalGateSound)
                .serverChat(TerminalSounds::onChat);
    }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.terminalSounds && c.section("Puzzles & Terminals");
    }

    /** Hypixel's terminal pling. Runs on the netty thread too: config and a static read only. */
    public static boolean cancelPling(SoundEvent sound, float volume, float pitch) {
        return sound == SoundEvents.NOTE_BLOCK_PLING.value() && volume == 8f && Math.abs(pitch - 4.047619f) < 0.01f
                && on() && TerminalUtils.getCurrentTerm() != null;
    }

    /** Before the solver handles a slot click. */
    public static void onClick(int slot, int button) {
        if (!on()) return;
        TerminalHandler term = TerminalUtils.getCurrentTerm();
        if (term == null || !term.canClick(slot, button)) return;
        if (TerminalSolver.enabled() && term.shouldProtect()) return;
        TeslaMapsConfig c = cfg();
        boolean last = term.solution.size() == 1 || (term.type == TerminalTypes.MELODY && slot == 34);
        if (last && c.terminalCompleteSound) {
            if (!c.terminalCancelLastClick) playClick(c);
            LoudSound.play(SoundOptions.resolve(c.terminalCompleteSoundId), c.terminalCompleteVolume, c.terminalCompletePitch);
        } else {
            playClick(c);
        }
    }

    private static void playClick(TeslaMapsConfig c) {
        long now = System.currentTimeMillis();
        if (now - lastPlayed <= 2) return;
        lastPlayed = now;
        LoudSound.play(SoundOptions.resolve(c.terminalClickSound), c.terminalClickVolume, c.terminalClickPitch);
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals("The gate has been destroyed!") || s.equals("The Core entrance is opening!")) {
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        }
    }
}
