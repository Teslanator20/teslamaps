// Ported from ADM's p3sim/clear/bots/BotBody.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The profile ids of the bots of the run now, so the client side can tell a bot's player entity from a real player. */
public final class BotRegistry {
    private BotRegistry() {}

    private static final Set<UUID> ids = ConcurrentHashMap.newKeySet();

    public static boolean isBot(UUID id) { return ids.contains(id); }

    public static void add(UUID id) { ids.add(id); }

    public static void remove(UUID id) { ids.remove(id); }

    public static void clear() { ids.clear(); }

    public static int size() { return ids.size(); }
}
