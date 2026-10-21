package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/RunArgs.kt

import com.teslamaps.adm.sim.clear.bots.BotStrategies;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class RunArgs {
    private RunArgs() {}

    public interface Parsed {
        final class Ok implements Parsed {
            public final RunConfig config;

            public Ok(RunConfig config) { this.config = config; }
        }

        final class Bad implements Parsed {
            public final String message;

            public Bad(String message) { this.message = message; }
        }
    }

    public static final String OPTIONS = "bots on|off, party 1-5, lag off|good|typical|bad|highslot, nodeaths, hp base|pack, strategy auto|neutral|<id>";

    public static Parsed parse(String mode, List<String> args, RunDefaults defaults, Supplier<Long> drawSeed) {
        return parse(mode, args, defaults, BotStrategies.available(), drawSeed);
    }

    public static Parsed parse(String mode, List<String> args, RunDefaults defaults, List<PartyStrategy> offered, Supplier<Long> drawSeed) {
        List<String> rest = args;
        LayoutSource layout;
        long seed;
        switch (mode) {
            case "random" -> { layout = LayoutSource.Random.INSTANCE; seed = drawSeed.get(); }
            case "seed" -> {
                Long n = rest.isEmpty() ? null : toLongOrNull(rest.get(0));
                if (n == null) return new Parsed.Bad("Give the seed as a number: /ads run seed 12345");
                rest = new ArrayList<>(rest.subList(1, rest.size()));
                layout = new LayoutSource.Seed(n);
                seed = n;
            }
            case "layout" -> {
                String id = rest.isEmpty() ? null : rest.get(0);
                if (id == null) return new Parsed.Bad("Give the recorded layout's id: /ads run layout <id> (the browser on the start screen lists them)");
                rest = new ArrayList<>(rest.subList(1, rest.size()));
                layout = new LayoutSource.Recorded(id);
                seed = drawSeed.get();
            }
            default -> { return new Parsed.Bad("Unknown way to start: " + mode); }
        }
        RunDefaults d = defaults;
        int i = 0;
        while (i < rest.size()) {
            String w = rest.get(i).toLowerCase(Locale.ROOT);
            String next = i + 1 < rest.size() ? rest.get(i + 1).toLowerCase(Locale.ROOT) : null;
            switch (w) {
                case "nobots" -> { d = new RunDefaults(false, d.partySize, d.lag, d.noDeaths, d.mobHpFromPack, d.strategy); i++; }
                case "bots" -> {
                    Boolean v = onOff(next);
                    if (v == null) return bad("bots", "on or off");
                    d = new RunDefaults(v, d.partySize, d.lag, d.noDeaths, d.mobHpFromPack, d.strategy);
                    i += 2;
                }
                case "party" -> {
                    Integer n = next == null ? null : toIntOrNull(next);
                    if (n == null || n < 1 || n > 5) return bad("party", "a size from 1 to 5");
                    d = new RunDefaults(n > 1, n, d.lag, d.noDeaths, d.mobHpFromPack, d.strategy);
                    i += 2;
                }
                case "lag" -> {
                    LagPreset p = lag(next);
                    if (p == null) return bad("lag", "off, good, typical, bad or highslot");
                    d = new RunDefaults(d.bots, d.partySize, p, d.noDeaths, d.mobHpFromPack, d.strategy);
                    i += 2;
                }
                case "nodeaths" -> {
                    Boolean v = onOff(next);
                    d = new RunDefaults(d.bots, d.partySize, d.lag, v != null ? v : true, d.mobHpFromPack, d.strategy);
                    i += v != null ? 2 : 1;
                }
                case "hp" -> {
                    boolean fromPack;
                    if ("base".equals(next)) fromPack = false;
                    else if ("pack".equals(next)) fromPack = true;
                    else return bad("hp", "base or pack");
                    d = new RunDefaults(d.bots, d.partySize, d.lag, d.noDeaths, fromPack, d.strategy);
                    i += 2;
                }
                case "strategy" -> {
                    String word = i + 1 < rest.size() ? rest.get(i + 1) : null;
                    if (word == null || word.isBlank()) return bad("strategy", "a strategy id");
                    String choice = StrategyChoice.normalize(word, offered);
                    if (choice == null) return new Parsed.Bad("There is no party strategy called \"" + word + "\". Choose from: " + StrategyChoice.words(offered) + ".");
                    d = new RunDefaults(d.bots, d.partySize, d.lag, d.noDeaths, d.mobHpFromPack, choice);
                    i += 2;
                }
                default -> { return new Parsed.Bad("I don't know \"" + rest.get(i) + "\". Options after the layout: " + OPTIONS + "."); }
            }
        }
        return new Parsed.Ok(d.config(layout, seed, offered));
    }

    private static Parsed bad(String option, String wants) { return new Parsed.Bad("\"" + option + "\" needs " + wants + ". Options: " + OPTIONS + "."); }

    private static Boolean onOff(String w) {
        if (w == null) return null;
        return switch (w) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    private static Long toLongOrNull(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static String letters(String s) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) if (Character.isLetter(s.charAt(k))) b.append(s.charAt(k));
        return b.toString().toLowerCase(Locale.ROOT);
    }

    public static LagPreset lag(String word) {
        if (word == null) return null;
        String key = letters(word);
        for (LagPreset p : LagPreset.values()) if (letters(p.name()).equals(key) || letters(p.label).equals(key)) return p;
        return null;
    }

    public static String describe(RunConfig c) {
        String layout;
        LayoutSource l = c.layout;
        if (l instanceof LayoutSource.Random) layout = "a random layout (seed " + c.seed + ")";
        else if (l instanceof LayoutSource.Seed s) layout = "the layout for seed " + s.seed;
        else if (l instanceof LayoutSource.Recorded r) layout = "the recorded layout " + r.id;
        else layout = ((LayoutSource.Practice) l).room + " (practice)";
        String party = !c.bots || c.partySize <= 1 ? "you alone" : c.partySize + " players with bots";
        List<String> extras = new ArrayList<>();
        extras.add("lag " + c.lag.label);
        if (c.noDeaths) extras.add("no deaths");
        if (c.strategy != null && !c.strategy.equals("neutral")) extras.add("party strategy " + c.strategy);
        extras.add(c.mobHpScale == null ? "mob HP from the pack" : "base mob HP");
        return "Starting a " + c.floor + " run on " + layout + ", " + party + ", " + String.join(", ", extras) + ".";
    }
}
