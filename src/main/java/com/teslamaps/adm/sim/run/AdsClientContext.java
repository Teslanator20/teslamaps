// Ported from ADM's p3sim/run/AdsCommands.kt
package com.teslamaps.adm.sim.run;

import java.util.List;
import java.util.function.Consumer;

/** What a command sees on the client thread. reply writes a chat line; args are the words after the name. */
public class AdsClientContext {
    public final List<String> args;
    public final Consumer<String> reply;

    public AdsClientContext(List<String> args, Consumer<String> reply) {
        this.args = args;
        this.reply = reply;
    }

    public String arg(int i) { return i >= 0 && i < args.size() ? args.get(i) : null; }
}
