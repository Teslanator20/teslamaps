// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PartySettings {
    private PartySettings() {}

    public static class Opt {
        public final String key;
        public final Supplier<JsonElement> get;
        public final Consumer<JsonElement> set;

        public Opt(String key, Supplier<JsonElement> get, Consumer<JsonElement> set) {
            this.key = key;
            this.get = get;
            this.set = set;
        }
    }

    private static Opt bool(String key, Supplier<BoolSetting> s) {
        return new Opt(key, () -> {
            BoolSetting b = s.get();
            return new JsonPrimitive(b != null && b.get());
        }, v -> {
            BoolSetting b = s.get();
            if (b != null) b.set(v.getAsBoolean());
        });
    }

    private static Opt num(String key, Supplier<NumSetting> s) {
        return new Opt(key, () -> {
            NumSetting n = s.get();
            return new JsonPrimitive(n != null ? n.get() : 0.0);
        }, v -> {
            NumSetting n = s.get();
            if (n == null) return;
            if (n.integral()) n.set((double) (int) Math.round(v.getAsDouble()));
            else n.set(v.getAsDouble());
        });
    }

    private static List<Opt> shared;
    private static List<Opt> personal;

    public static synchronized List<Opt> SHARED() {
        if (shared == null) shared = List.of(
                bool("bots", () -> P3Sim.botsS),
                new Opt("deathTicks", () -> new JsonPrimitive(P3Sim.deathTicksS.index()), v -> P3Sim.deathTicksS.setIndex(v.getAsInt())),
                new Opt("terminal", () -> new JsonPrimitive(P3Sim.terminalS.index()), v -> P3Sim.terminalS.setIndex(v.getAsInt())),
                num("goldorKill", () -> P3Sim.goldorKillS),
                bool("clickLimit", () -> P3Sim.clickLimitS),
                bool("noMelodies", () -> P3Sim.noMelodiesS),
                num("termLuck", () -> P3Sim.termLuckS),
                num("ssLuck", () -> P3Sim.ssLuckS),
                bool("firstClickMelody", () -> P3Sim.firstClickMelodyS),
                bool("debugBots", () -> P3Sim.debugBotsS),
                num("breakerRefill", () -> P3Sim.breakerRefillS),
                num("breakerRegen", () -> P3Sim.breakerRegenS),
                bool("breakerPerma", () -> P3Sim.breakerPermaS),
                bool("realMoves", () -> P3Sim.realMovesS),
                bool("lava", () -> P3Sim.lavaS),
                bool("p3Only", () -> P3Sim.p3OnlyS),
                bool("termsOnly", () -> P3Sim.termsOnlyS));
        return shared;
    }

    public static synchronized List<Opt> PERSONAL() {
        if (personal == null) personal = List.of(
                new Opt("class", () -> new JsonPrimitive(P3Sim.ownClass().name()), v -> {
                    DungeonClass c = Players.classByName(v.getAsString());
                    if (c != null) P3Sim.classS.setIndex(Party.CLASSES().indexOf(c));
                }),
                num("speed", () -> P3Sim.speedS),
                bool("phoenix", () -> P3Sim.phoenixS),
                bool("realMasks", () -> P3Sim.realMasksS),
                new Opt("wornMask", () -> new JsonPrimitive(P3Sim.wornMaskS.index()), v -> P3Sim.wornMaskS.setIndex(v.getAsInt())),
                num("ping", () -> P3Sim.pingS),
                bool("jitter", () -> P3Sim.jitterS),
                num("shortbow", () -> P3Sim.shortbowCooldownS),
                num("hydra", () -> P3Sim.hydraStartS),
                bool("mageAll", () -> P3Sim.mageAllS),
                bool("infinite", () -> P3Sim.breakerInfiniteS),
                bool("terror", () -> P3Sim.terrorAtTermsS),
                bool("blackCat", () -> P3Sim.blackCatS),
                bool("showTimes", () -> P3Sim.showTimesS));
        return personal;
    }

    public static JsonObject snapshot(List<Opt> opts) {
        JsonObject o = new JsonObject();
        for (Opt it : opts) {
            try {
                o.add(it.key, it.get.get());
            } catch (Throwable ignored) {
            }
        }
        return o;
    }

    public static void apply(List<Opt> opts, JsonObject o) {
        for (Opt opt : opts) {
            JsonElement v = o.get(opt.key);
            if (v == null) continue;
            try {
                opt.set.accept(v);
            } catch (Throwable ignored) {
            }
        }
    }

    public static JsonObject diff(JsonObject was, JsonObject now) {
        JsonObject d = new JsonObject();
        for (Map.Entry<String, JsonElement> e : now.entrySet()) {
            if (!Objects.equals(was == null ? null : was.get(e.getKey()), e.getValue())) d.add(e.getKey(), e.getValue());
        }
        return d;
    }
}
