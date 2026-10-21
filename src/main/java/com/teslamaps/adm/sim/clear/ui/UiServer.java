package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/UiServer.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.SimNet;

public final class UiServer {
    private UiServer() {}

    public static void send(String name, String... args) {
        if (!P3Sim.inSim()) return;
        Ec.safely("ads run " + name, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("group", "run");
            o.addProperty("name", name);
            JsonArray a = new JsonArray();
            for (String s : args) a.add(s);
            o.add("args", a);
            SimNet.send("adscmd", o.toString());
        });
    }
}
