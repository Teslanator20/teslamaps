package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Uploads a screenshot on request and hands back a link. Nothing leaves the machine
 * without an explicit click.
 */
public class ImageShare {

    private static final String LITTERBOX = "https://litterbox.catbox.moe/resources/internals/api.php";
    private static final String CATBOX = "https://catbox.moe/user/api.php";
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    /** Only files from this host are ever fetched again, a random chat link is not. */
    public static final String TRUSTED_HOST = "catbox.moe";

    public static void upload(File file, Consumer<String> onDone, Consumer<String> onError) {
        if (file == null || !file.isFile()) {
            onError.accept("File is gone");
            return;
        }
        if (file.length() > MAX_UPLOAD_BYTES) {
            onError.accept("Larger than 20 MB");
            return;
        }

        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean permanent = "permanent".equalsIgnoreCase(c.imageShareExpiry);

        CompletableFuture.runAsync(() -> {
            String result = post(permanent ? CATBOX : LITTERBOX, file, permanent ? null : c.imageShareExpiry);
            if (result != null && result.startsWith("http")) {
                deliver(result, onDone);
                return;
            }
            // litterbox has been known to answer 500 for a while at a time; catbox is the
            // same service without the expiry, so the upload still goes through
            if (!permanent) {
                TeslaMaps.LOGGER.warn("Litterbox refused the upload ({}), retrying on catbox", result);
                String fallback = post(CATBOX, file, null);
                if (fallback != null && fallback.startsWith("http")) {
                    Minecraft.getInstance().execute(() -> {
                        feedback("§e[TeslaMaps] Litterbox is down, uploaded permanently instead.");
                        onDone.accept(fallback);
                    });
                    return;
                }
                result = fallback;
            }
            String message = result == null ? "no answer" : result;
            Minecraft.getInstance().execute(() -> onError.accept(message));
        });
    }

    private static void deliver(String url, Consumer<String> onDone) {
        Minecraft.getInstance().execute(() -> onDone.accept(url));
    }

    /** @return the url, or a short reason. Never the raw page, that ends up in chat. */
    private static String post(String endpoint, File file, String expiry) {
        try {
            String boundary = "teslamaps" + System.nanoTime();
            List<byte[]> parts = new ArrayList<>();
            parts.add(field(boundary, "reqtype", "fileupload"));
            if (expiry != null) parts.add(field(boundary, "time", expiry));
            parts.add(filePart(boundary, "fileToUpload", file));
            parts.add(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

            int size = parts.stream().mapToInt(p -> p.length).sum();
            byte[] body = new byte[size];
            int at = 0;
            for (byte[] p : parts) {
                System.arraycopy(p, 0, body, at, p.length);
                at += p.length;
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String answer = response.body() == null ? "" : response.body().trim();
            if (answer.startsWith("http")) return answer;

            TeslaMaps.LOGGER.warn("Upload to {} failed, HTTP {}: {}", endpoint, response.statusCode(), shorten(answer));
            return "HTTP " + response.statusCode();
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Upload to {} failed: {}", endpoint, t.toString());
            return t.getClass().getSimpleName();
        }
    }

    /** Error pages are whole html documents, so keep a readable stub for the log. */
    private static String shorten(String body) {
        String plain = body.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) plain = "empty response";
        return plain.length() > 120 ? plain.substring(0, 120) + "..." : plain;
    }

    private static byte[] field(String boundary, String name, String value) {
        return ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] filePart(String boundary, String name, File file) throws IOException {
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + file.getName() + "\"\r\nContent-Type: image/png\r\n\r\n";
        byte[] head = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] data = Files.readAllBytes(file.toPath());
        byte[] out = new byte[head.length + data.length + 2];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(data, 0, out, head.length, data.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }

    public static final String TAG = "[img]";

    /**
     * Packs a catbox link into a domain free token, e.g. "L-l108hg-p".
     * L = litter, F = files, p = png, j = jpg.
     */
    public static String toToken(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) return null;

            char site = host.startsWith("litter") ? 'L' : host.startsWith("files") ? 'F' : 0;
            if (site == 0) return null;

            int dot = path.lastIndexOf('.');
            if (dot < 0) return null;
            String id = path.substring(1, dot);
            String ext = path.substring(dot + 1).toLowerCase();
            char kind = ext.equals("png") ? 'p' : (ext.equals("jpg") || ext.equals("jpeg")) ? 'j' : 0;
            if (kind == 0 || !id.matches("[A-Za-z0-9]{1,32}")) return null;

            return site + "-" + id + "-" + kind;
        } catch (Throwable t) {
            return null;
        }
    }

    /** @return the rebuilt link, or null when the token is not one of ours. */
    public static String fromToken(String token) {
        if (token == null) return null;
        String[] parts = token.split("-");
        if (parts.length != 3 || parts[0].length() != 1 || parts[2].length() != 1) return null;
        if (!parts[1].matches("[A-Za-z0-9]{1,32}")) return null;

        String host = switch (parts[0].charAt(0)) {
            case 'L' -> "https://litter.catbox.moe/";
            case 'F' -> "https://files.catbox.moe/";
            default -> null;
        };
        String ext = switch (parts[2].charAt(0)) {
            case 'p' -> ".png";
            case 'j' -> ".jpg";
            default -> null;
        };
        if (host == null || ext == null) return null;
        return host + parts[1] + ext;
    }

    public static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(message));
    }
}
