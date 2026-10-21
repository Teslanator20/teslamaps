package com.teslamaps.adm.sim;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.screens.Screen;

/** Calls from the sim into ADM features that live outside it (splits, term info...). No-ops until another part sets them. */
public final class SimHooks {
    private SimHooks() {}

    public static final String MAXOR = com.teslamaps.adm.leap.splits.SplitTracker.MAXOR;
    public static final String STORM = com.teslamaps.adm.leap.splits.SplitTracker.STORM;
    public static final String TERMS = com.teslamaps.adm.leap.splits.SplitTracker.TERMS;
    public static final String GOLDOR = com.teslamaps.adm.leap.splits.SplitTracker.GOLDOR;
    public static final String NECRON = com.teslamaps.adm.leap.splits.SplitTracker.NECRON;

    public static volatile BiConsumer<String, Integer> onSplitsStart = (s, n) -> {};
    public static volatile Runnable onSplitsStop = () -> {};
    public static volatile Runnable onTickTimersStop = () -> {};
    public static volatile Runnable onMasksReset = () -> {};
    public static volatile IntConsumer onTermStart = n -> {};
    public static volatile Runnable onTermClearTime = () -> {};
    public static volatile IntConsumer onTermSectionTime = n -> {};
    public static volatile Consumer<Screen> onJoinHypixel = s -> {};

    public static void splitsStart(String split, int n) { onSplitsStart.accept(split, n); }
    public static void splitsStop() { onSplitsStop.run(); }
    public static void tickTimersStop() { onTickTimersStop.run(); }
    public static void masksReset() { onMasksReset.run(); }
    public static void termStart(int n) { onTermStart.accept(n); }
    public static void termClearTime() { onTermClearTime.run(); }
    public static void termSectionTime(int n) { onTermSectionTime.accept(n); }
    public static void joinHypixel(Screen parent) { onJoinHypixel.accept(parent); }
}
