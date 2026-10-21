// Ported from ADM's p3sim/clear/puzzles/WeirdosLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Three Weirdos as a logic puzzle: the three NPCs always say one of exactly five fixed sets of three sentences, and which NPC says
 * which line varies. The NPC that says the set's reward line has the reward chest. A referenced name is written in red with a reset
 * after it. Pure: no Minecraft.
 */
public final class WeirdosLogic {
    private WeirdosLogic() {}

    /** One sentence of a set: {X} = another NPC's name, refers = index of the line whose speaker that is (-1 none). */
    public static class Line {
        public final String text;
        public final int refers;
        public final boolean reward;

        public Line(String text, int refers, boolean reward) {
            this.text = text;
            this.refers = refers;
            this.reward = reward;
        }

        public Line(String text, int refers) { this(text, refers, false); }

        public Line(String text) { this(text, -1, false); }
    }

    /** The five sets Hypixel uses, A to E. */
    public static final List<List<Line>> sets = List.of(
            // A
            List.of(
                    new Line("They are both telling the truth. The reward isn't in {X}'s §rchest.", 1),
                    new Line("The reward is not in my chest!", -1, true),
                    new Line("One of us is telling the truth!")),
            // B
            List.of(
                    new Line("The reward isn't in any of our chests.", -1, true),
                    new Line("The reward is in my chest!"),
                    new Line("The reward is not in my chest. They are both lying.")),
            // C
            List.of(
                    new Line("My chest has the reward!"),
                    new Line("{X} §ris telling the truth.", 0),
                    new Line("Both of them are telling the truth. Also, {X} §rhas the reward in their chest!", 1, true)),
            // D
            List.of(
                    new Line("My chest doesn't have the reward. At least one of the others is telling the truth!"),
                    new Line("My chest doesn't have the reward. We are all telling the truth.", -1, true),
                    new Line("One of the others is lying!")),
            // E
            List.of(
                    new Line("We are all telling the truth!"),
                    new Line("{X} §ris telling the truth and the reward is in his chest.", 0),
                    new Line("At least one of them is lying, and the reward is not in {X}'s §rchest!", 1, true)));

    /** A drawn setting: set indexes sets and slotOfLine says which NPC slot (0..2) says each line of the set. */
    public static class Scenario {
        public final int set;
        public final List<Integer> slotOfLine;

        public Scenario(int set, List<Integer> slotOfLine) {
            this.set = set;
            this.slotOfLine = slotOfLine;
        }

        private List<Line> lineSet() { return sets.get(set); }

        /** The slot of the NPC that says the reward line. */
        public int reward() {
            List<Line> ls = lineSet();
            int idx = -1;
            for (int i = 0; i < ls.size(); i++) if (ls.get(i).reward) { idx = i; break; }
            return slotOfLine.get(idx);
        }

        /** What each NPC slot says, with Hypixel's colour codes, given the three NPC names by slot. */
        public List<String> lines(List<String> names) {
            String[] out = new String[3];
            List<Line> ls = lineSet();
            for (int i = 0; i < ls.size(); i++) {
                Line l = ls.get(i);
                String text = l.refers >= 0 ? l.text.replace("{X}", "§c§c" + names.get(slotOfLine.get(l.refers))) : l.text;
                out[slotOfLine.get(i)] = text;
            }
            List<String> res = new ArrayList<>();
            for (String s : out) {
                if (s == null) throw new NullPointerException();
                res.add(s);
            }
            return res;
        }
    }

    /** One setting at random. */
    public static Scenario draw(KRandom rnd) {
        int set = rnd.nextInt(sets.size());
        List<Integer> slots = new ArrayList<>(List.of(0, 1, 2));
        KRandom.shuffle(slots, rnd);
        return new Scenario(set, slots);
    }

    private static List<Scenario> scenarios;

    /** Every setting: five sets times the six ways to hand the three lines to the three NPCs. */
    public static synchronized List<Scenario> scenarios() {
        if (scenarios == null) {
            List<List<Integer>> perms = List.of(List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2), List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0));
            List<Scenario> out = new ArrayList<>();
            for (int s = 0; s < sets.size(); s++) for (List<Integer> p : perms) out.add(new Scenario(s, p));
            scenarios = out;
        }
        return scenarios;
    }

    /** The names the NPCs go by in Hypixel's rooms (21 seen). */
    public static final List<String> NAMES = List.of(
            "Victoria", "Rose", "Luverne", "Lino", "Carver", "Melrose", "Madelia", "Ramsey", "Morris", "Hugo", "Hope",
            "Eveleth", "Marshall", "Elmo", "Ardis", "Winona", "Virginia", "Benson", "Willmar", "Montgomery", "Baxter");

    /** Three distinct names. */
    public static List<String> drawNames(KRandom rnd) {
        List<String> l = new ArrayList<>(NAMES);
        KRandom.shuffle(l, rnd);
        return new ArrayList<>(l.subList(0, 3));
    }
}
