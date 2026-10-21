// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.Pair;
import net.minecraft.server.level.ServerPlayer;

public final class SimActions {
    private SimActions() {}

    @SafeVarargs
    public static void send(String action, Pair<String, ?>... args) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        for (Pair<String, ?> a : args) {
            String k = a.first;
            Object v = a.second;
            if (v == null) continue;
            if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else o.addProperty(k, v.toString());
        }
        SimNet.send("act", o.toString());
    }

    private static JsonElement field(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e;
    }

    private static String s(JsonObject o, String k) {
        JsonElement e = field(o, k);
        return e == null ? null : e.getAsString();
    }

    private static Double d(JsonObject o, String k) {
        JsonElement e = field(o, k);
        return e == null ? null : e.getAsDouble();
    }

    private static boolean b(JsonObject o, String k) {
        JsonElement e = field(o, k);
        return e != null && e.getAsBoolean();
    }

    private static final String PRACTICE_HOSTS = "Practice is the host's: their start, jobs and checkpoints.";

    public static void run(ServerPlayer p, String data) {
        JsonObject o;
        try {
            o = JsonParser.parseString(data).getAsJsonObject();
        } catch (Throwable t) {
            return;
        }
        String who = p.getGameProfile().name();
        String a = s(o, "a");
        if (a == null) return;
        switch (a) {
            case "start" -> {
                Fight.Start what;
                try {
                    String w = s(o, "what");
                    what = Fight.Start.valueOf(w != null ? w : "P3");
                } catch (Throwable t) {
                    return;
                }
                if (!Sim.isHost(p)) Sim.everyone(() -> {
                    Sim.note("§f" + who + "§7 started §f" + what.label + "§7.");
                });
                Fight.start(what);
            }
            case "restart" -> {
                if (Practice.active()) {
                    if (Sim.isHost(p)) Practice.restart();
                    else Sim.note(PRACTICE_HOSTS);
                } else {
                    if (!Sim.isHost(p)) Sim.everyone(() -> {
                        Sim.note("§f" + who + "§7 restarted.");
                    });
                    Fight.start(Fight.lastStart);
                }
            }
            case "practiceRestart" -> {
                if (Sim.isHost(p)) Practice.restart();
                else Sim.note(PRACTICE_HOSTS);
            }
            case "stop" -> {
                if (!Sim.isHost(p)) Sim.everyone(() -> {
                    Sim.note("§f" + who + "§7 stopped the run.");
                });
                Fight.end();
            }
            case "tp" -> {
                if (Sim.isHost(p)) GhostCapture.invalidate("a menu teleport");
                Double x = d(o, "x");
                if (x == null) return;
                Double y = d(o, "y");
                if (y == null) return;
                Double z = d(o, "z");
                if (z == null) return;
                Double yaw = d(o, "yaw");
                Double pitch = d(o, "pitch");
                Sim.tp(p, x, y, z, yaw == null ? null : (Float) yaw.floatValue(), pitch == null ? null : (Float) pitch.floatValue());
            }
            case "hotbarSave" -> Sim.chat("§8[§6ADS§8] " + HotbarLayout.save(p, b(o, "p3")));
            case "hotbarReset" -> {
                Sim.chat("§8[§6ADS§8] " + HotbarLayout.reset(b(o, "p3")));
                SimItems.giveHotbar(p, b(o, "p3"), true);
            }
            case "resetItems" -> {
                HotbarLayout.dropLive(b(o, "p3"));
                SimItems.giveHotbar(p, b(o, "p3"), true);
            }
            case "practiceCustom", "practicePreset" -> {
                if (!Sim.isHost(p)) Sim.note(PRACTICE_HOSTS);
                else if (a.equals("practiceCustom")) Practice.startCustom();
                else {
                    String name = s(o, "name");
                    if (name == null) return;
                    Practice.startPreset(name);
                }
            }
            default -> {}
        }
    }
}
