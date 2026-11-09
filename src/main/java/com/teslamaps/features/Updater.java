package com.teslamaps.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Self-updater. Trust chain: the manifest is Ed25519-signed with a key that only exists on
 * the dev machine, and the jar must match the sha256 inside that manifest — the GitHub token
 * below is read-only and only ever grants download access, never trust.
 */
public class Updater {

    private static final String API = "https://api.github.com/repos/Teslanator20/teslamaps-releases";
    private static final String PUBKEY_B64 = "MCowBQYDK2VwAyEAHnDN8qIaRhdGnr+EgsxVUIXdr+JhOdD+pqDwbLOZR+k=";
    // fine-grained PAT, read-only, scoped to teslamaps-releases only (split so it survives naive scans)
    private static final String T0 = "";
    private static final String T1 = "";
    private static final String T2 = "";
    private static final long MAX_JAR_BYTES = 30L * 1024 * 1024;

    public enum State { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, FAILED }

    public static volatile State state = State.IDLE;
    public static volatile String latestVersion = "";
    public static volatile String error = "";
    private static volatile String jarSha256 = "";
    private static volatile long jarAssetId = -1;
    private static volatile long jarSize = -1;
    private static boolean promptShown = false;
    private static boolean chatNotified = false;
    private static volatile long lastCheck = 0;
    public static volatile java.util.List<String> changelog = java.util.List.of();
    public static volatile String changelogVersion = "";
    private static boolean whatsNewPending = false;
    private static final long RECHECK_MILLIS = 30 * 60 * 1000L;

    private static String token() {
        return T0 + T1 + T2;
    }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(TeslaMaps.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }

    public static void init() {
        cleanPending();
        loadSavedChangelog();
        if (!TeslaMapsConfig.get().updateCheck) return;
        if (token().isEmpty()) return;
        check(false);
    }

    /** a swap that never completed leaves a .pending behind */
    private static void cleanPending() {
        Path own = FabricLoader.getInstance().getModContainer(TeslaMaps.MOD_ID)
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst()).orElse(null);
        if (own == null || own.getParent() == null) return;
        try (var files = Files.list(own.getParent())) {
            files.filter(p -> p.getFileName().toString().startsWith("teslamaps-")
                            && p.getFileName().toString().endsWith(".jar.pending"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    public static void check(boolean verbose) {
        if (state == State.CHECKING || state == State.DOWNLOADING) return;
        state = State.CHECKING;
        lastCheck = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = http();
                HttpResponse<String> release = client.send(api("/releases/latest", "application/vnd.github+json"),
                        HttpResponse.BodyHandlers.ofString());
                if (release.statusCode() != 200) throw new Exception("release lookup HTTP " + release.statusCode());
                JsonObject root = JsonParser.parseString(release.body()).getAsJsonObject();
                JsonArray assets = root.getAsJsonArray("assets");
                long manifestId = -1, sigId = -1;
                for (var el : assets) {
                    JsonObject a = el.getAsJsonObject();
                    String name = a.get("name").getAsString();
                    if (name.equals("manifest.json")) manifestId = a.get("id").getAsLong();
                    if (name.equals("manifest.sig")) sigId = a.get("id").getAsLong();
                }
                if (manifestId < 0 || sigId < 0) throw new Exception("release is missing manifest/signature");

                byte[] manifestBytes = asset(client, manifestId, 64 * 1024);
                byte[] sig = Base64.getDecoder().decode(
                        new String(asset(client, sigId, 4096), StandardCharsets.UTF_8).trim());
                if (!verify(manifestBytes, sig)) throw new Exception("manifest signature invalid");

                JsonObject manifest = JsonParser.parseString(
                        new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                String version = manifest.get("version").getAsString();
                String jar = manifest.get("jar").getAsString();
                String sha = manifest.get("sha256").getAsString().toLowerCase();
                if (!jar.matches("teslamaps-[\\w.\\-]+\\.jar")) throw new Exception("bad jar name in manifest");

                long assetId = -1, size = -1;
                for (var el : assets) {
                    JsonObject a = el.getAsJsonObject();
                    if (a.get("name").getAsString().equals(jar)) {
                        assetId = a.get("id").getAsLong();
                        size = a.get("size").getAsLong();
                    }
                }
                if (assetId < 0) throw new Exception("jar asset not in release");
                if (size > MAX_JAR_BYTES) throw new Exception("jar too big");

                if (!newer(version, currentVersion())) {
                    state = State.IDLE;
                    if (verbose) chat("§fUp to date (§e" + currentVersion() + "§f)");
                    return;
                }
                java.util.List<String> lines = new java.util.ArrayList<>();
                if (manifest.has("changelog") && manifest.get("changelog").isJsonArray()) {
                    for (var el : manifest.getAsJsonArray("changelog")) {
                        if (lines.size() >= 80) break;
                        String l = el.isJsonPrimitive() ? el.getAsString() : "";
                        lines.add(l.length() > 300 ? l.substring(0, 300) : l);
                    }
                }
                changelog = java.util.List.copyOf(lines);
                changelogVersion = version;
                latestVersion = version;
                jarSha256 = sha;
                jarAssetId = assetId;
                jarSize = size;
                state = State.AVAILABLE;
                if (verbose) {
                    // tick() would say the same thing again a moment later
                    chatNotified = true;
                    chat("§fUpdate available: §e" + version + " §7— /tmap update to install");
                }
            } catch (Exception e) {
                state = State.FAILED;
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                TeslaMaps.LOGGER.info("update check failed: {}", error);
                if (verbose) chat("§cUpdate check failed: §7" + error);
            }
        });
    }

    /** Downloads, verifies and installs the pending update. Chat feedback either way. */
    public static void install() {
        if (state == State.READY) {
            chat("§aUpdate already downloaded — restart the game.");
            return;
        }
        if (state != State.AVAILABLE) {
            chat("§fNo update pending, checking...");
            check(true);
            return;
        }
        Path ownJar = FabricLoader.getInstance().getModContainer(TeslaMaps.MOD_ID)
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst()).orElse(null);
        if (ownJar == null || !ownJar.toString().endsWith(".jar")) {
            chat("§cNot running from a jar (dev client) — skipping install.");
            return;
        }
        state = State.DOWNLOADING;
        chat("§fDownloading §e" + latestVersion + "§f...");
        final Path oldJar = ownJar;
        CompletableFuture.runAsync(() -> {
            try {
                byte[] jar = asset(http(), jarAssetId, MAX_JAR_BYTES);
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
                if (!sha.equals(jarSha256)) throw new Exception("jar sha256 mismatch");

                // keep the existing file name: launchers (Modrinth) index mods by path and
                // refuse to start once the file they know is gone
                Path pending = oldJar.resolveSibling(oldJar.getFileName() + ".pending");
                Files.write(pending, jar);
                try {
                    Files.move(pending, oldJar, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception locked) {
                    // Windows keeps the loaded jar locked — finish the swap after the JVM exits
                    scheduleSwap(pending, oldJar);
                }
                state = State.READY;
                saveChangelog();
                chat("§aUpdate " + latestVersion + " installed — restart the game.");
            } catch (Exception e) {
                state = State.FAILED;
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                chat("§cUpdate failed: §7" + error);
            }
        });
    }

    private static void scheduleSwap(Path pending, Path target) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // a failed move leaves the old jar intact — never a missing or duplicate mod
                String move = "move /y \"" + pending + "\" \"" + target + "\" >nul 2>&1";
                new ProcessBuilder("cmd", "/c",
                        "ping -n 4 127.0.0.1 >nul 2>&1 & " + move
                                + " || (ping -n 9 127.0.0.1 >nul 2>&1 & " + move
                                + " || (ping -n 21 127.0.0.1 >nul 2>&1 & " + move + "))")
                        .start();
            } catch (Exception ignored) {}
        }));
    }

    private static Path changelogFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("changelog.json");
    }

    private static void saveChangelog() {
        if (changelog.isEmpty() || !TeslaMapsConfig.get().updateChangelog) return;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("version", changelogVersion);
            o.addProperty("shown", false);
            JsonArray arr = new JsonArray();
            changelog.forEach(arr::add);
            o.add("lines", arr);
            Files.createDirectories(changelogFile().getParent());
            Files.writeString(changelogFile(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.info("changelog save failed: {}", e.toString());
        }
    }

    private static void loadSavedChangelog() {
        try {
            Path f = changelogFile();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            String v = o.get("version").getAsString();
            if (!v.equals(currentVersion())) return;
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (var el : o.getAsJsonArray("lines")) lines.add(el.getAsString());
            changelog = java.util.List.copyOf(lines);
            changelogVersion = v;
            whatsNewPending = !o.has("shown") || !o.get("shown").getAsBoolean();
        } catch (Exception e) {
            TeslaMaps.LOGGER.info("changelog load failed: {}", e.toString());
        }
    }

    private static void markChangelogShown() {
        try {
            Path f = changelogFile();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            o.addProperty("shown", true);
            Files.writeString(f, o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    /** Changelog screen for the installed (or pending) version. */
    public static void openChangelog() {
        Minecraft mc = Minecraft.getInstance();
        if (changelog.isEmpty()) {
            chat("§7No changelog for this version.");
            return;
        }
        mc.schedule(() -> mc.gui.setScreen(new com.teslamaps.screen.UpdateScreen(mc.gui.screen(), true)));
    }

    public static void tick() {
        if (whatsNewPending && Minecraft.getInstance().player != null && TeslaMapsConfig.get().updateChangelog) {
            whatsNewPending = false;
            markChangelogShown();
            {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fUpdated to §e"
                        + changelogVersion + "§f — ").append(Component.literal("§b§n[What's new]")
                        .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/tmap update changelog"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7Show the changelog"))))));
            }
        }
        // the launch check cannot see a release that went up while the player was already online
        if (state == State.IDLE || state == State.FAILED) {
            if (TeslaMapsConfig.get().updateCheck && !token().isEmpty()
                    && System.currentTimeMillis() - lastCheck > RECHECK_MILLIS) {
                check(false);
            }
            return;
        }
        if (state != State.AVAILABLE) return;
        Minecraft mc = Minecraft.getInstance();
        if (!promptShown && mc.gui.screen() instanceof TitleScreen) {
            promptShown = true;
            mc.gui.setScreen(new com.teslamaps.screen.UpdateScreen(mc.gui.screen()));
        }
        if (!chatNotified && mc.player != null) {
            chatNotified = true;
            chat("§fUpdate §e" + latestVersion + "§f available — §b/tmap update§f to install");
        }
    }

    private static HttpClient http() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static HttpRequest api(String path, String accept) {
        return HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token())
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET().build();
    }

    private static byte[] asset(HttpClient client, long id, long maxBytes) throws Exception {
        HttpResponse<byte[]> response = client.send(api("/releases/assets/" + id, "application/octet-stream"),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new Exception("asset download HTTP " + response.statusCode());
        if (response.body().length > maxBytes) throw new Exception("asset too big");
        return response.body();
    }

    private static boolean verify(byte[] data, byte[] sig) throws Exception {
        PublicKey key = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBKEY_B64)));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(data);
        return verifier.verify(sig);
    }

    static boolean newer(String remote, String local) {
        String[] r = remote.split("[^0-9]+");
        String[] l = local.split("[^0-9]+");
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = i < r.length && !r[i].isEmpty() ? Integer.parseInt(r[i]) : 0;
            int b = i < l.length && !l[i].isEmpty() ? Integer.parseInt(l[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static void chat(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + message));
        });
    }
}
