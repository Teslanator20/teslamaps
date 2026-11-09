package com.teslamaps.features;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Copying, listing and deleting screenshots. The browser lives in ScreenshotScreen. */
public class ScreenshotManager {

    private static File lastScreenshot = null;

    public static File lastScreenshot() {
        return lastScreenshot != null && lastScreenshot.isFile() ? lastScreenshot : null;
    }

    /** Called from ScreenshotCopyMixin once the file is on disk. */
    public static void onSaved(NativeImage image, File file) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        lastScreenshot = file;

        if (c.autoCopyScreenshot) copy(image);
        ScreenshotPreview.present(file);
        if (c.screenshotChatActions) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.sendSystemMessage(actionLine(file));
            });
        }
    }

    private static MutableComponent actionLine(File file) {
        String name = file.getName();
        return Component.literal("§a[TeslaMaps] §7" + name + " ")
                .append(button("§b[Copy]", "/tmap screenshots copy " + name, "§7Copy the picture to the clipboard"))
                .append(Component.literal(" "))
                .append(button("§d[Copy Ingame]", "/tmap screenshots share " + name,
                        "§7Upload and copy the chat token"))
                .append(Component.literal(" "))
                .append(button("§c[Delete]", "/tmap screenshots delete " + name, "§7Delete this screenshot"))
                .append(Component.literal(" "))
                .append(button("§e[Folder]", "/tmap screenshots folder", "§7Open the screenshots folder"))
                .append(Component.literal(" "))
                .append(button("§d[Browse]", "/tmap screenshots", "§7Open the screenshot browser"));
    }

    private static MutableComponent button(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    public static File directory() {
        return new File(Minecraft.getInstance().gameDirectory, "screenshots");
    }

    /** Newest first. */
    public static List<File> list() {
        File dir = directory();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) return List.of();
        List<File> out = new ArrayList<>(Arrays.asList(files));
        out.sort(Comparator.comparingLong(File::lastModified).reversed());
        return out;
    }

    public static File byName(String name) {
        File file = new File(directory(), name);
        return file.isFile() ? file : null;
    }

    public static boolean delete(File file) {
        if (file == null || !file.isFile()) return false;
        boolean ok = file.delete();
        if (ok && file.equals(lastScreenshot)) lastScreenshot = null;
        return ok;
    }

    /** Uploads, then leaves the chat token on the clipboard. */
    public static void shareToClipboard(File file) {
        if (file == null) {
            ImageShare.feedback("§c[TeslaMaps] Screenshot is gone.");
            return;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String known = c.imageShareHistory ? storedLink(file) : null;
        if (known != null) {
            copyToken(known);
            return;
        }
        ImageShare.feedback("§7[TeslaMaps] Uploading " + file.getName() + "...");
        ImageShare.upload(file,
                url -> {
                    if (c.imageShareHistory) {
                        c.imageShareLinks.put(file.getName(), url + "|" + file.length() + "|" + file.lastModified()
                                + "|" + System.currentTimeMillis() + "|" + (url.contains("//litter.") ? c.imageShareExpiry : "permanent"));
                        TeslaMapsConfig.save();
                    }
                    copyToken(url);
                },
                error -> ImageShare.feedback("§c[TeslaMaps] Upload failed: " + error
                        + (c.imageShareHistory ? " §7(click Copy Ingame again to retry)" : "")));
    }

    private static void copyToken(String url) {
        String token = ImageShare.toToken(url);
        if (token == null) {
            ImageShare.feedback("§c[TeslaMaps] Could not build a token.");
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(ImageShare.TAG + " " + token);
        if (!TeslaMapsConfig.get().imageShareHistory) {
            ImageShare.feedback("§a[TeslaMaps] §7Copied - paste it into any chat.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Token copied - paste it into any chat. ")
                .append(Component.literal("§b[Open Link]").withStyle(st -> st
                        .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7" + url)))))
                .append(Component.literal(" "))
                .append(Component.literal("§e[Copy Link]").withStyle(st -> st
                        .withClickEvent(new ClickEvent.CopyToClipboard(url))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Public link for Discord etc."))))));
    }

    // Skysoft ScreenshotUploadMetadataStore: link per file, dropped once the file changed or expired
    private static String storedLink(File file) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String entry = c.imageShareLinks.get(file.getName());
        if (entry == null) return null;
        String[] p = entry.split("\\|");
        try {
            if (p.length == 5 && Long.parseLong(p[1]) == file.length() && Long.parseLong(p[2]) == file.lastModified()) {
                long hours = switch (p[4]) {
                    case "1h" -> 1; case "12h" -> 12; case "24h" -> 24; case "72h" -> 72; default -> -1;
                };
                // a few minutes of slack, litterbox deletes on the hour
                if (hours < 0 || System.currentTimeMillis() - Long.parseLong(p[3]) < hours * 3_600_000L - 600_000L) return p[0];
            }
        } catch (NumberFormatException ignored) {
        }
        c.imageShareLinks.remove(file.getName());
        return null;
    }

    public static void openFolder() {
        File dir = directory();
        dir.mkdirs();
        com.mojang.blaze3d.Blaze3D.openPath(dir.toPath());
    }

    /** Reads, converts and copies off the main thread; onDone gets null or the error, on the main thread. */
    public static void copyAsync(File file, java.util.function.Consumer<String> onDone) {
        if (file == null || !file.isFile()) {
            onDone.accept("file is gone");
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // NativeImage instead of ImageIO: same decoder the rest of the mod uses, and it does
            // not depend on ImageIO's plugin registry or its cache directory being writable
            try (java.io.FileInputStream in = new java.io.FileInputStream(file);
                 NativeImage image = NativeImage.read(in)) {
                copyPixels(image.getPixelsABGR(), image.getWidth(), image.getHeight());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("could not read " + file.getName() + ": " + e.getMessage(), e);
            }
        }, net.minecraft.util.Util.ioPool()).whenComplete((v, err) -> {
            String msg = err == null ? null : message(err);
            if (msg != null) TeslaMaps.LOGGER.warn("Screenshot copy failed: {}", msg);
            Minecraft.getInstance().execute(() -> onDone.accept(msg));
        });
    }

    private static String message(Throwable t) {
        while (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    /** Runs on the screenshot IO thread already; the pixels are copied before the image is freed. */
    public static void copy(NativeImage image) {
        try {
            copyPixels(image.getPixelsABGR(), image.getWidth(), image.getHeight());
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Screenshot copy failed: {}", message(t));
        }
    }

    private static void copyPixels(int[] abgr, int w, int h) {
        if (com.teslamaps.utils.WindowsClipboard.supported()) {
            try {
                com.teslamaps.utils.WindowsClipboard.copyAbgr(abgr, w, h);
                return;
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Native clipboard failed, trying AWT: {}", message(t));
            }
        }
        if (!toClipboard(toBuffered(abgr, w, h))) throw new IllegalStateException("clipboard not available");
    }

    private static BufferedImage toBuffered(int[] abgr, int w, int h) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] argb = new int[abgr.length];
        for (int i = 0; i < abgr.length; i++) {
            int p = abgr[i];
            argb[i] = ((p >>> 24) << 24) | ((p & 0xFF) << 16) | (((p >> 8) & 0xFF) << 8) | ((p >> 16) & 0xFF);
        }
        bi.setRGB(0, 0, w, h, argb, 0, w);
        return bi;
    }

    private static boolean toClipboard(BufferedImage image) {
        if (image == null) return false;
        // AWT refuses to hand out a clipboard in headless mode and the launcher may well
        // have set it; flipping it back before the toolkit is created is what makes the
        // copy work at all.
        if (Boolean.getBoolean("java.awt.headless")) System.setProperty("java.awt.headless", "false");
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new ImageTransferable(image), null);
            return true;
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Clipboard copy failed: {}", t.toString());
            return false;
        }
    }

    private record ImageTransferable(Image image) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(f)) throw new UnsupportedFlavorException(f);
            return image;
        }
    }
}
