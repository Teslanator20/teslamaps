// Ported from ADM's p3sim/run/plugin/AdsPlugins.kt
package com.teslamaps.adm.sim.run.plugin;

import com.teslamaps.adm.sim.Ec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Finds and holds the installed plug-ins (entrypoint "teslamaps:ads"). All reads are safe with none. */
public final class AdsPlugins {
    private AdsPlugins() {}

    /** Bumped when a rule of the compatibility contract is broken. */
    public static final int API_VERSION = 1;
    public static final String ENTRYPOINT = "teslamaps:ads";

    public static volatile List<AdsPlugin> plugins = List.of();

    private static boolean loaded = false;

    /** Loads the entrypoint. Never throws; a bad plug-in is logged and skipped. A second call does nothing. */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        List<AdsPlugin> found;
        try {
            found = FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, AdsPlugin.class);
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] could not read the '" + ENTRYPOINT + "' entrypoint", t);
            found = List.of();
        }
        List<AdsPlugin> ok = new ArrayList<>();
        for (AdsPlugin p : found) {
            try {
                if (p.apiVersion() > API_VERSION) {
                    Ec.LOG.warn("[ads] plug-in " + p.id() + " needs ADS API " + p.apiVersion() + " but this game has " + API_VERSION + "; ignored");
                    continue;
                }
                p.onLoad(new PluginContext() {
                    @Override public int apiVersion() { return API_VERSION; }

                    @Override public void log(String message) { Ec.LOG.info("[ads:" + p.id() + "] " + message); }
                });
                ok.add(p);
                Ec.LOG.info("[ads] plug-in " + p.id() + " loaded");
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] a plug-in failed to load and was ignored", t);
            }
        }
        plugins = ok;
    }

    /** For tests: installs plug-ins without Fabric. */
    public static void installForTest(List<AdsPlugin> list) {
        plugins = list;
        loaded = true;
    }

    /** The first blood-camp factory that supports room, or null. */
    public static BloodCampFactory bloodCamp(BloodRoomSpec room) {
        for (AdsPlugin p : plugins) {
            BloodCampFactory f;
            try {
                f = p.bloodCamp();
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] plug-in " + p.id() + " failed in bloodCamp()", t);
                f = null;
            }
            if (f == null) continue;
            boolean supports;
            try {
                supports = f.supports(room);
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] plug-in " + p.id() + " failed in supports()", t);
                supports = false;
            }
            if (supports) return f;
        }
        return null;
    }

    /** Every strategy offered by any plug-in, in entrypoint order. */
    public static List<PartyStrategy> strategies() {
        List<PartyStrategy> out = new ArrayList<>();
        for (AdsPlugin p : plugins) {
            try {
                out.addAll(p.partyStrategies());
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] plug-in " + p.id() + " failed in partyStrategies()", t);
            }
        }
        return out;
    }

    public static PartyStrategy strategy(String id) {
        for (PartyStrategy s : strategies()) if (s.id().equals(id)) return s;
        return null;
    }
}
