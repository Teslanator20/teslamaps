package com.teslamaps.features;

// Like SkyHanni's ChatManager + SkyHanniLogger chat logs (all / blocked / modified, deleted after X days)
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every chat line into logs/teslamaps-chat/, optionally with filter status and reason. */
public final class ChatLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeslaMaps chat log");
        t.setDaemon(true);
        return t;
    });

    private static Component original, rewritten;
    private static String modReason;
    private static LocalDate cleaned;

    private ChatLog() {}

    public static void register() {
        ClientSendMessageEvents.CHAT.register(m -> outgoing(m));
        ClientSendMessageEvents.COMMAND.register(c -> outgoing("/" + c));
    }

    private static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chatLog && c.section("Chat");
    }

    private static boolean status() {
        return TeslaMapsConfig.get().chatLogFilterStatus;
    }

    /** Raw line, before any rewrite. */
    public static void begin(Component message) {
        original = on() ? message : null;
        rewritten = null;
        modReason = null;
    }

    public static Component rewritten(Component out) {
        rewritten = out;
        return out;
    }

    /** The raw line when {@code message} is what the early rewrite made of it. */
    private static Component in(Component message) {
        Component in = original != null && message == rewritten ? original : message;
        original = null;
        rewritten = null;
        return in;
    }

    /** Why the line about to be stored differs from what came in. */
    public static void noteRewrite(String reason) {
        if (modReason == null) modReason = reason;
    }

    public static void blocked(Component message, String reason) {
        if (!on()) return;
        String text = in(message).getString();
        if (!status()) {
            write("all", text);
            return;
        }
        String r = reason == null ? "other" : reason;
        write("all", "[BLOCKED " + r + "] " + text);
        write("blocked", "[" + r + "] " + text);
    }

    /** Right before vanilla stores the line. */
    public static void shown(Component out) {
        if (!on()) return;
        Component in = original != null ? original : out;
        original = null;
        rewritten = null;
        String before = in.getString();
        if (!status()) {
            write("all", before);
            return;
        }
        String after = out.getString();
        if (before.equals(after)) {
            write("all", "[ALLOWED] " + before);
            return;
        }
        String r = modReason == null ? "rewrite" : modReason;
        write("all", "[MODIFIED " + r + "] " + before + "  =>  " + after);
        write("modified", "[" + r + "] " + before + "  =>  " + after);
    }

    private static void outgoing(String text) {
        if (on() && status()) write("all", "[OUTGOING] " + text);
    }

    private static void write(String kind, String text) {
        LocalDate day = LocalDate.now();
        String line = "[" + LocalTime.now().format(TIME) + "] " + text.replace("\n", "\\n") + System.lineSeparator();
        int keep = Math.max(1, TeslaMapsConfig.get().chatLogDays);
        boolean clean = !day.equals(cleaned);
        cleaned = day;
        IO.execute(() -> {
            try {
                Path dir = dir();
                Files.createDirectories(dir);
                if (clean) cleanup(dir, keep);
                Files.writeString(dir.resolve(kind + "-" + day + ".log"), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        });
    }

    private static void cleanup(Path dir, int keepDays) {
        long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
        File[] files = dir.toFile().listFiles((d, n) -> n.endsWith(".log"));
        if (files == null) return;
        for (File f : files) if (f.lastModified() < cutoff) f.delete();
    }

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("teslamaps-chat");
    }

    public static void openFolder() {
        Path dir = dir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}
        net.minecraft.util.Util.getPlatform().openPath(dir);
    }
}
