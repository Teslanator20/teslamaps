package com.teslamaps.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.features.capes.CapeSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.http.HttpResponse;
import java.util.Locale;

/** Shares the active profile over the backend (config edge function) by a 6-letter code. */
public final class ConfigShare {

    private static final String P = "§b[TeslaMaps] §r";
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile boolean busy;

    private ConfigShare() {}

    /** Profile settings only: @Global fields (PBs, keybinds, cape, api keys, item customizations) stay home. */
    static JsonObject export(TeslaMapsConfig c) {
        JsonObject tree = GSON.toJsonTree(c).getAsJsonObject();
        for (Field f : TeslaMapsConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.isAnnotationPresent(Global.class) || personal(f.getName())) tree.remove(f.getName());
        }
        tree.remove("activeProfile");
        return tree;
    }

    private static boolean personal(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("webhook") || n.contains("apikey") || n.contains("token") && !n.contains("tokenstack")
                || n.contains("password") || n.startsWith("cape");
    }

    public static void share() {
        if (busy) return;
        if (!CapeSync.serverConfigured()) { msg("§cBackend not configured"); return; }
        busy = true;
        JsonObject body = new JsonObject();
        body.addProperty("action", "share");
        body.add("config", export(TeslaMapsConfig.get()));
        FabricLoader.getInstance().getModContainer("teslamaps").ifPresent(m ->
                body.addProperty("modVersion", m.getMetadata().getVersion().getFriendlyString()));
        msg("§7Uploading your settings...");
        new Thread(() -> {
            try {
                JsonObject res = call(body);
                if (res == null) return;
                String code = res.get("code").getAsString();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    mc.keyboardHandler.setClipboard("/tmap config import " + code);
                    String cmd = "/tmap config import " + code;
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal(P + "§fShared! Code: §a§l" + code
                            + " §7(command copied)").withStyle(s -> s
                            .withClickEvent(new ClickEvent.CopyToClipboard(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to copy §f" + cmd)))));
                    msg("§7Your friend runs §f" + cmd + "§7. Sharing again replaces the old code.");
                });
            } finally {
                busy = false;
            }
        }, "TeslaMaps config share").start();
    }

    public static void importCode(String code) {
        if (busy) return;
        if (!CapeSync.serverConfigured()) { msg("§cBackend not configured"); return; }
        busy = true;
        JsonObject body = new JsonObject();
        body.addProperty("action", "get");
        body.addProperty("code", code.trim().toUpperCase(Locale.ROOT));
        msg("§7Downloading settings...");
        new Thread(() -> {
            try {
                JsonObject res = call(body);
                if (res == null) return;
                JsonElement cfg = res.get("config");
                String owner = res.has("owner") ? res.get("owner").getAsString() : code;
                if (cfg == null || !cfg.isJsonObject()) { msg("§cBroken share"); return; }
                String name = owner + "'s config";
                Minecraft.getInstance().execute(() -> {
                    if (ConfigProfiles.importShared(name, cfg.getAsJsonObject())) {
                        msg("§fLoaded §a" + owner + "§f's settings as profile §a" + name
                                + "§7. Your old settings stay in their own profile (/tmap → Profiles).");
                    } else msg("§cCould not load the shared settings");
                });
            } finally {
                busy = false;
            }
        }, "TeslaMaps config import").start();
    }

    private static JsonObject call(JsonObject body) {
        try {
            HttpResponse<String> r = CapeSync.callAuthed("config", body);
            if (r == null) { msg("§cMojang auth failed"); return null; }
            JsonObject json = null;
            try { json = JsonParser.parseString(r.body()).getAsJsonObject(); } catch (Exception ignored) {}
            if (r.statusCode() != 200 || json == null) {
                String m = json != null && json.has("message") ? json.get("message").getAsString() : "Server said " + r.statusCode();
                msg("§c" + m);
                return null;
            }
            return json;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Config share call failed: {}", e.toString());
            msg("§cNo connection to the backend");
            return null;
        }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal(P + s)); });
    }
}
