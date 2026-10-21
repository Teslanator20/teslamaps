// Ported from ADM's p3sim/clear/puzzles/WeirdosPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;

/**
 * Three Weirdos in the world: three chests with an NPC stand beside each (the chest one block east of the stand in the room's frame),
 * the sentences as `[NPC] name: text` lines when an NPC is clicked. The right chest after all three spoke solves it; a wrong one
 * fails it until the Architect's First Draft puts the chests back with the same sentences.
 */
public class WeirdosPuzzle extends WorldPuzzle {
    private WeirdosLogic.Scenario scenario = null;
    private List<String> names = List.of();
    private List<WorldPos> chests = List.of();
    private final boolean[] talked = new boolean[3];
    private final List<ArmorStand> nameStands = new ArrayList<>();
    private int solvedAt = -1;

    public WeirdosPuzzle(PuzzleContext context) { super(PuzzleKinds.WEIRDOS, context); }

    /** Hypixel names the NPC that holds the reward, in red, whatever chest was clicked. */
    @Override public String solvedPhrase() {
        WeirdosLogic.Scenario sc = scenario;
        if (sc == null) return null;
        return "wasn't fooled by §r§c" + names.get(sc.reward()) + "§r§e!";
    }

    /** Chat first, then the room turns green 3 ticks later. */
    @Override public int greenLeadTicks() { return -GREEN_AFTER_CHAT_TICKS; }

    @Override public List<WorldPos> rewardSpots() {
        WeirdosLogic.Scenario sc = scenario;
        if (sc == null) return List.of();
        int r = sc.reward();
        return r >= 0 && r < chests.size() ? List.of(chests.get(r)) : List.of();
    }

    @Override protected void build() {
        KRandom rnd = run().subRandom("weirdos:" + context().room.index);
        scenario = WeirdosLogic.draw(rnd);
        names = WeirdosLogic.drawNames(rnd);
        List<WorldPos> cs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            List<WorldPos> a = context().anchor("chest" + i);
            if (!a.isEmpty()) cs.add(a.get(0));
        }
        chests = cs;
        if (chests.size() < 3) { scenario = null; return; }
        placeChests();
        for (int i = 0; i < chests.size(); i++) {
            // The stand is the block west of its chest in the room's frame: Odin takes the chest as the stand's block plus one in x.
            Pos anchor = anchorPos("chest" + (i + 1));
            if (anchor == null) continue;
            WorldPos s = world(anchor.x - 1, anchor.y, anchor.z);
            if (s == null) continue;
            nameStands.add(spawnStand("§c" + names.get(i), s.x + 0.5, s.y, s.z + 0.5, false, false));
            spawnStand("§e§lCLICK", s.x + 0.5, s.y - CLICK_DROP, s.z + 0.5);
        }
        Arrays.fill(talked, false);
        solvedAt = -1;
    }

    private Pos anchorPos(String name) {
        RoomDesign design = context().room.design;
        if (design == null || design.puzzle == null || design.puzzle.anchors == null) return null;
        List<Pos> l = design.puzzle.anchors.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private void placeChests() { for (WorldPos c : chests) setWorld(c, Blocks.CHEST.defaultBlockState()); }

    private void clearChests(int except) {
        for (int i = 0; i < chests.size(); i++) if (i != except) setWorld(chests.get(i), Blocks.AIR.defaultBlockState());
    }

    private void clearChests() { clearChests(-1); }

    @Override public void onTick() {
        if (solvedAt >= 0 && now() >= solvedAt + NAME_BLANK_TICKS) {
            solvedAt = -1;
            for (ArmorStand it : nameStands) {
                try { it.setCustomName(Sim.legacy("")); it.setCustomNameVisible(false); } catch (Throwable ignored) {}
            }
        }
    }

    private void speak(int i, String text) { say("§e[NPC] §r§c" + names.get(i) + "§r§f: §r" + text); }

    @Override public boolean onNpcClick(UUID player, String npcName) {
        int i = names.indexOf(npcName);
        if (i < 0) return false;
        WeirdosLogic.Scenario sc = scenario;
        if (sc == null) return true;
        if (status() != PuzzleStatus.UNSOLVED) return true;
        talked[i] = true;
        speak(i, sc.lines(names).get(i));
        return true;
    }

    @Override public boolean onInteract(UUID player, int x, int y, int z) {
        WeirdosLogic.Scenario sc = scenario;
        if (sc == null) return false;
        int i = -1;
        for (int k = 0; k < chests.size(); k++) {
            WorldPos c = chests.get(k);
            if (c.x == x && c.y == y && c.z == z) { i = k; break; }
        }
        if (i < 0) return false;
        if (status() != PuzzleStatus.UNSOLVED) return status() != PuzzleStatus.SOLVED || i != sc.reward();
        boolean all = true;
        for (boolean t : talked) if (!t) { all = false; break; }
        if (!all) { speak(i, "Talk to all of us first!"); return true; }
        if (i == sc.reward()) {
            clearChests(i);
            solvedAt = now();
            solve(player);
        } else {
            speak(i, "You fool!");
            clearChests();
            fail(player, "was fooled by §r§c" + names.get(i) + "§r§e!");
        }
        return true;
    }

    @Override public void applySolved() {
        WeirdosLogic.Scenario sc = scenario;
        if (sc == null) return;
        clearChests(sc.reward());
    }

    @Override public void onReset() {
        // The chests come back and the NPCs say the same three sentences again, so everyone has to be talked to again.
        if (scenario == null) return;
        placeChests();
        Arrays.fill(talked, false);
    }

    /** Ticks from the solve line to the room turning green (measured median 3). */
    public static final int GREEN_AFTER_CHAT_TICKS = 3;

    /** The NPC names go blank this long after the solve (measured 46). */
    public static final int NAME_BLANK_TICKS = 46;

    /** The CLICK label stand hangs this far below the name stand. */
    public static final double CLICK_DROP = 0.25;
}
