// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerFiles {
    private PlayerFiles() {}

    public static final String HOTBAR = "p3sim-hotbar.json";
    public static final String LOADOUTS = "p3sim-loadouts.json";
    public static final List<String> NAMES = List.of(HOTBAR, LOADOUTS);

    private static File file(String name) { return Ec.configDir().resolve(name).toFile(); }

    public static String readLocal(String name) {
        try {
            File f = file(name);
            return f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void writeLocal(String name, String text) {
        if (!NAMES.contains(name)) return;
        Ec.safely("p3sim file " + name, () -> writeAtomic(file(name), text));
    }

    public static void setAsideLocal(String name) {
        if (owner() == null) setAside(file(name));
    }

    private static final HashSet<String> setAside = new HashSet<>();

    public static void setAside(File f) {
        if (!f.exists() || !setAside.add(f.getPath())) return;
        File to = new File(f.getPath() + ".corrupt");
        try {
            Files.copy(f.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            return;
        }
        Ec.LOG.warn("[ec] p3sim: " + f.getName() + " couldn't be read, kept as " + to.getName());
    }

    public static void writeAtomic(File f, String text) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(String name) {
        ServerPlayer p = Sim.acting();
        if (p == null || Sim.isHost(p)) return readLocal(name);
        return Players.profile(p).files.get(name);
    }

    public static void write(String name, String text) {
        ServerPlayer p = Sim.acting();
        if (p == null || Sim.isHost(p)) {
            writeLocal(name, text);
            return;
        }
        Players.profile(p).files.put(name, text);
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("text", text);
        SimNet.send(p, "file", o.toString());
    }

    public static UUID owner() {
        ServerPlayer p = Sim.acting();
        return p == null || Sim.isHost(p) ? null : p.getUUID();
    }
}
