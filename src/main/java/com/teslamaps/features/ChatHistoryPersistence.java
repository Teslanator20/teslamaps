package com.teslamaps.features;

// Ported from Skysoft's ChatHistoryPersistence.kt
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the chat into config/teslamaps/chat_history.json when the client stops and
 * restores it on the next launch. Restored lines lose their signature and count as
 * ancient, so they render fully faded and can never be deleted by the server.
 */
public class ChatHistoryPersistence {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("chat_history.json");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final int VERSION = 1;
    private static final int RESTORED_TIME = -1_000_000;
    private static final int MAX_LINES = 1000;
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;

    public static void save(Minecraft mc) {
        if (!TeslaMapsConfig.get().chatHistoryPersist) return;
        try {
            ChatComponent chat = mc.gui.getChat();
            List<GuiMessage> messages = ((ChatComponentAccessor) chat).teslamaps$getAllMessages();

            JsonArray arr = new JsonArray();
            int limit = Math.min(messages.size(), MAX_LINES);
            for (int i = 0; i < limit; i++) {
                JsonObject encoded = encode(messages.get(i));
                if (encoded != null) arr.add(encoded);
            }

            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.add("messages", arr);

            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save chat history: {}", e.toString());
        }
    }

    public static void restore(Minecraft mc) {
        if (!TeslaMapsConfig.get().chatHistoryPersist) return;
        try {
            if (!Files.isRegularFile(FILE) || Files.size(FILE) > MAX_FILE_BYTES) return;
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version") == null || root.get("version").getAsInt() != VERSION) return;

            List<GuiMessage> restored = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("messages")) {
                GuiMessage msg = decode(el.getAsJsonObject());
                if (msg != null) restored.add(msg);
                if (restored.size() >= MAX_LINES) break;
            }
            if (restored.isEmpty()) return;

            ChatComponentAccessor accessor = (ChatComponentAccessor) mc.gui.getChat();
            List<GuiMessage> all = accessor.teslamaps$getAllMessages();
            all.clear();
            all.addAll(restored);
            accessor.teslamaps$refreshTrimmedMessages();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to restore chat history: {}", e.toString());
        }
    }

    // clearMessages(true) on disconnect would wipe the history mid-session - keep the lines.
    private static List<GuiMessage> preserved = null;

    public static void captureBeforeClear(ChatComponent chat) {
        if (!TeslaMapsConfig.get().chatHistoryPersist) return;
        List<GuiMessage> all = ((ChatComponentAccessor) chat).teslamaps$getAllMessages();
        preserved = new ArrayList<>(all.subList(0, Math.min(all.size(), MAX_LINES)));
    }

    public static void restoreAfterClear(ChatComponent chat) {
        if (preserved == null) return;
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        accessor.teslamaps$getAllMessages().addAll(preserved);
        accessor.teslamaps$refreshTrimmedMessages();
        preserved = null;
    }

    private static JsonObject encode(GuiMessage message) {
        try {
            JsonObject out = new JsonObject();
            out.add("content", ComponentSerialization.CODEC
                    .encodeStart(JsonOps.INSTANCE, message.content()).getOrThrow());
            out.addProperty("source", message.source().name());
            GuiMessageTag tag = message.tag();
            if (tag != null) {
                JsonObject t = new JsonObject();
                t.addProperty("indicatorColor", tag.indicatorColor());
                if (tag.icon() != null) t.addProperty("icon", tag.icon().name());
                if (tag.text() != null) {
                    t.add("text", ComponentSerialization.CODEC
                            .encodeStart(JsonOps.INSTANCE, tag.text()).getOrThrow());
                }
                if (tag.logTag() != null) t.addProperty("logTag", tag.logTag());
                out.add("tag", t);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static GuiMessage decode(JsonObject obj) {
        try {
            Component content = ComponentSerialization.CODEC
                    .parse(JsonOps.INSTANCE, obj.get("content")).result().orElse(null);
            if (content == null) return null;

            GuiMessageSource source;
            try {
                source = GuiMessageSource.valueOf(obj.get("source").getAsString());
            } catch (Exception e) {
                source = GuiMessageSource.SYSTEM_CLIENT;
            }

            GuiMessageTag tag = null;
            if (obj.has("tag")) {
                JsonObject t = obj.getAsJsonObject("tag");
                int color = t.has("indicatorColor") ? t.get("indicatorColor").getAsInt() : 0;
                GuiMessageTag.Icon icon = null;
                if (t.has("icon")) {
                    try {
                        icon = GuiMessageTag.Icon.valueOf(t.get("icon").getAsString());
                    } catch (Exception ignored) {
                    }
                }
                Component tagText = t.has("text")
                        ? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, t.get("text")).result().orElse(null)
                        : null;
                String logTag = t.has("logTag") ? t.get("logTag").getAsString() : null;
                tag = new GuiMessageTag(color, icon, tagText, logTag);
            }

            return new GuiMessage(RESTORED_TIME, content, null, source, tag);
        } catch (Exception e) {
            return null;
        }
    }
}
