// Ported from ADM's p3sim/run/AdsCommands.kt
package com.teslamaps.adm.sim.run;

import java.util.function.Consumer;

/**
 * One `/ads dev <name>` or `/ads run <name>` command. client runs at once on the client thread; server runs on the sim server
 * thread as the player (args over SimNet kind adscmd). requiresSim false only for commands that read files.
 */
public class AdsCommand {
    public final String name;
    /** `/ads dev rooms`, arguments in angle brackets. */
    public final String usage;
    /** One plain sentence for the list. */
    public final String summary;
    public final boolean requiresSim;
    public final Consumer<AdsClientContext> client;
    public final Consumer<AdsServerContext> server;

    public AdsCommand(String name, String usage, String summary, boolean requiresSim, Consumer<AdsClientContext> client,
                      Consumer<AdsServerContext> server) {
        this.name = name;
        this.usage = usage;
        this.summary = summary;
        this.requiresSim = requiresSim;
        this.client = client;
        this.server = server;
    }

    public AdsCommand(String name, String usage, String summary, boolean requiresSim, Consumer<AdsClientContext> client) {
        this(name, usage, summary, requiresSim, client, null);
    }

    public AdsCommand(String name, String usage, String summary, boolean requiresSim) { this(name, usage, summary, requiresSim, null, null); }

    public AdsCommand(String name, String usage, String summary) { this(name, usage, summary, true, null, null); }
}
