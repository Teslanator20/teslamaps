package com.teslamaps.safari;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Ported from critterMod's RecatchSpots.java
/**
 * A failed capsule puts the critter back exactly where it left from, and the fast ones
 * are gone again before you have found them a second time. The spot is known the moment
 * the throw lands, so it gets pinned and the next capsule can be in the air first.
 */
public class RecatchSpots {

    private static final long HOLD_MILLIS = 40_000;      // nothing resolved it, drop the pin
    private static final long SIGHTING_MILLIS = 10_000;  // older than this is not what the throw hit
    private static final long LOADED_MILLIS = 1_000;     // seen within a sweep or two = in the world now
    private static final double STALE_DISTANCE = 5.0;    // that far from the pin means it runs again
    private static final double BEHIND_PENALTY = 1000.0;

    /** Commons die to the first throw, the set pieces do not come straight back, Snoozle never moves. */
    private static final Set<String> NEVER_PINNED = Set.of("Hideyho", "Wumpa", "Doomspiral", "Snoozle");

    private record Seen(AABB box, long millis) {}

    private static final Map<String, Seen> lastSeen = new HashMap<>();
    private static final Map<String, Double> sweepBest = new HashMap<>();

    private static SafariData.Critter pinnedCritter;
    private static AABB pinnedBox;
    private static long pinnedAt;
    /** Held between the throw and its outcome - the capture site wears the species name too. */
    private static boolean resolving;

    public static AABB pinned() { return pinnedBox; }

    public static SafariData.Critter pinnedCritter() { return pinnedCritter; }

    public static void beginSweep() { sweepBest.clear(); }

    /** One critter body from the ESP sweep. Several of a species can be in view, only one was thrown at. */
    public static void see(SafariData.Critter critter, AABB box) {
        if (!TeslaMapsConfig.get().safariRecatchSpot) return;
        if (resolving && pinnedCritter != null && pinnedCritter.name().equals(critter.name())) return;

        double score = aimScore(Minecraft.getInstance().player, box);
        Double current = sweepBest.get(critter.name());
        if (current != null && current <= score) return;
        sweepBest.put(critter.name(), score);
        lastSeen.put(critter.name(), new Seen(box, System.currentTimeMillis()));
    }

    public static void tick() {
        if (pinnedBox == null) return;
        long now = System.currentTimeMillis();
        if (now - pinnedAt > HOLD_MILLIS) { clear(); return; }
        if (resolving) return;   // the pin is worth most in exactly this window

        Seen seen = lastSeen.get(pinnedCritter.name());
        if (seen != null && now - seen.millis() < LOADED_MILLIS
                && seen.box().getCenter().distanceTo(pinnedBox.getCenter()) > STALE_DISTANCE) {
            clear();
        }
    }

    public static void onThrow(SafariData.Critter critter) {
        pin(critter);
        resolving = pinnedBox != null;
    }

    /** The throw failed, so it is on its way back. Hold the pin and restart its clock. */
    public static void onEscape(SafariData.Critter critter) {
        if (!isPinned(critter)) return;
        resolving = false;
        pinnedAt = System.currentTimeMillis();
    }

    public static void onCaught(SafariData.Critter critter) {
        if (isPinned(critter)) clear();
    }

    private static boolean isPinned(SafariData.Critter critter) {
        return critter != null && pinnedCritter != null && pinnedCritter.name().equals(critter.name());
    }

    private static void pin(SafariData.Critter critter) {
        if (critter == null || critter.rarity() == SafariData.Rarity.COMMON
                || NEVER_PINNED.contains(critter.name())) {
            clear();
            return;
        }
        Seen seen = lastSeen.get(critter.name());
        // nobody saw it recently, so the throw went at something out of view - a guessed spot is worse than none
        if (seen == null || System.currentTimeMillis() - seen.millis() > SIGHTING_MILLIS) {
            clear();
            return;
        }
        pinnedCritter = critter;
        pinnedBox = seen.box();
        pinnedAt = System.currentTimeMillis();
    }

    /** How far off the line you are looking a box sits. Anything behind you loses to anything in front. */
    private static double aimScore(Player player, AABB box) {
        if (player == null) return Double.MAX_VALUE;
        Vec3 toBox = box.getCenter().subtract(player.getEyePosition());
        Vec3 look = player.getViewVector(1.0f);
        double along = toBox.dot(look);
        if (along <= 0) return BEHIND_PENALTY + toBox.length();
        return toBox.subtract(look.scale(along)).length();
    }

    public static void clear() {
        pinnedCritter = null;
        pinnedBox = null;
        resolving = false;
    }

    public static void reset() {
        lastSeen.clear();
        sweepBest.clear();
        clear();
    }
}
