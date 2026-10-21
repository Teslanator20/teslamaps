package com.teslamaps.adm.sim;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What ADM's EngineerClient gave the sim: chat, log, safely. */
public final class Ec {
    private Ec() {}

    public static final Logger LOG = LoggerFactory.getLogger("TeslaMaps/ADS");
    public static final boolean BETA = true;
    public static final String PREFIX = "§8[§6ADM§8] ";

    public static Minecraft mc() { return Minecraft.getInstance(); }

    public static void safely(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOG.error("[ads] {} failed", what, t);
        }
    }

    public static void chat(String msg) { chat(Component.literal(msg)); }

    public static void chat(Component msg) {
        Minecraft mc = mc();
        mc.schedule(() -> mc.gui.getChat().addClientSystemMessage(msg));
    }

    public static void msg(String text) { chat(PREFIX + text); }

    public static void msg(Component text) { chat(Component.literal(PREFIX).append(text)); }

    public static Path configDir() { return mc().gameDirectory.toPath().resolve("config/teslamaps/adm"); }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("teslamaps", "adm/" + path); }
}
