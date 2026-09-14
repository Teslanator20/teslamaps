package com.teslamaps.features.capes;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.Node;

/**
 * Animated cape from a .gif. Every frame is its own uploaded texture and the render
 * path only picks one by wall clock, so an animated cape costs the same per frame as
 * a static one. Decoding happens off-thread, uploading on the main thread.
 */
public class GifCape {

    /** More than this and the frames are merged, so a 500 frame gif cannot eat the VRAM. */
    public static final int MAX_FRAMES = 120;
    /** Animated capes stay below the static cape limit - every frame is resident. */
    public static final int MAX_WIDTH = 512;
    /** 64x32 grid -> 512x256 per composed frame. */
    private static final int SHEET_SCALE = 8;

    private static final int MIN_DELAY_MS = 20;
    private static final int DEFAULT_DELAY_MS = 100;

    /**
     * What one decoded cape may cost. A local cape is the one the player picked, so it
     * gets the full budget; a synced cape is one of many worn by strangers around us,
     * so it is decoded smaller - 60 frames of 256x128 are about 8 MB of vram instead of
     * the 60 a full one would take.
     */
    public record Budget(int maxFrames, int maxWidth, int sheetScale) {
        public static final Budget LOCAL = new Budget(MAX_FRAMES, MAX_WIDTH, SHEET_SCALE);
        public static final Budget SYNCED = new Budget(60, 256, 4);
    }

    private final Identifier[] frames;
    private final int[] ends;        // cumulative ms, last entry == total
    private final int total;
    public final int width, height;

    private GifCape(Identifier[] frames, int[] ends, int width, int height) {
        this.frames = frames;
        this.ends = ends;
        this.total = ends[ends.length - 1];
        this.width = width;
        this.height = height;
    }

    public int frameCount() { return frames.length; }

    /** The texture for right now. Called per avatar per frame - keep it arithmetic. */
    public Identifier current() {
        if (frames.length == 1) return frames[0];
        int t = (int) Math.floorMod(System.currentTimeMillis(), total);
        int i = Arrays.binarySearch(ends, t);
        if (i < 0) i = -i - 1;
        return frames[Math.min(i, frames.length - 1)];
    }

    public void release() {
        for (Identifier id : frames) Minecraft.getInstance().getTextureManager().release(id);
    }

    /**
     * Finished cape sheets with their delays. Built off the main thread; the images are
     * CPU side until {@link #upload} hands them to the texture manager.
     */
    public record Decoded(List<NativeImage> frames, List<Integer> delays) {
        public void close() {
            for (NativeImage frame : frames) frame.close();
        }
    }

    /** Logical screen size, checked before a gif is stored. */
    public static int[] canvasSize(byte[] data) throws Exception {
        ImageReader reader = reader(data);
        try {
            int[] size = logicalScreen(reader);
            if (size[0] <= 0 || size[1] <= 0) {
                size = new int[]{reader.getWidth(0), reader.getHeight(0)};
            }
            return size;
        } finally {
            reader.dispose();
        }
    }

    private static int[] logicalScreen(ImageReader reader) {
        try {
            IIOMetadataNode screen = child((IIOMetadataNode) reader.getStreamMetadata()
                    .getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            if (screen != null) {
                return new int[]{intAttr(screen, "logicalScreenWidth", 0),
                        intAttr(screen, "logicalScreenHeight", 0)};
            }
        } catch (Throwable ignored) {
        }
        return new int[]{0, 0};
    }

    public static boolean isGif(byte[] data) {
        return data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }

    /**
     * Full decode with gif disposal handling: a frame may be a sub-rectangle that is
     * drawn onto what the previous one left behind, so every frame is composited onto
     * a canvas instead of used as-is.
     */
    public static Decoded decode(byte[] data, CapeCompose.Crop crop) throws Exception {
        return decode(data, crop, Budget.LOCAL);
    }

    /** As above, but with an explicit frame/size budget - see {@link Budget}. */
    public static Decoded decode(byte[] data, CapeCompose.Crop crop, Budget budget) throws Exception {
        List<NativeImage> built = new ArrayList<>();
        ImageReader reader = reader(data);
        try {
            int count = reader.getNumImages(true);
            if (count <= 0) throw new IllegalArgumentException("Empty gif");
            int[] screen = logicalScreen(reader);
            int canvasW = screen[0], canvasH = screen[1];
            if (canvasW <= 0 || canvasH <= 0) {
                canvasW = reader.getWidth(0);
                canvasH = reader.getHeight(0);
            }

            int step = Math.max(1, (count + budget.maxFrames() - 1) / budget.maxFrames());
            // a cropped frame is composed onto a fixed sheet, so the source may stay bigger
            int sourceLimit = crop == null ? budget.maxWidth() : budget.maxWidth() * 2;
            int scale = 1;
            while (canvasW / scale > sourceLimit) scale *= 2;

            List<NativeImage> frames = built;
            List<Integer> delays = new ArrayList<>();
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            BufferedImage previous = null;
            int carried = 0;

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                int left = 0, top = 0, delay = DEFAULT_DELAY_MS;
                String disposal = "none";
                try {
                    IIOMetadata meta = reader.getImageMetadata(i);
                    IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
                    IIOMetadataNode descriptor = child(root, "ImageDescriptor");
                    if (descriptor != null) {
                        left = intAttr(descriptor, "imageLeftPosition", 0);
                        top = intAttr(descriptor, "imageTopPosition", 0);
                    }
                    IIOMetadataNode control = child(root, "GraphicControlExtension");
                    if (control != null) {
                        int centis = intAttr(control, "delayTime", 0);
                        delay = centis <= 1 ? DEFAULT_DELAY_MS : centis * 10;
                        String value = control.getAttribute("disposalMethod");
                        if (value != null && !value.isEmpty()) disposal = value;
                    }
                } catch (Throwable ignored) {
                }

                if (disposal.equals("restoreToPrevious")) previous = copy(canvas);
                g.drawImage(frame, left, top, null);

                carried += Math.max(MIN_DELAY_MS, delay);
                if (i % step == 0 || i == count - 1) {
                    BufferedImage shot = scale > 1 ? shrink(canvas, canvasW / scale) : canvas;
                    frames.add(toSheet(toNative(shot, shot != canvas), crop, budget.sheetScale()));
                    delays.add(carried);
                    carried = 0;
                }

                switch (disposal) {
                    case "restoreToBackgroundColor" -> {
                        java.awt.Composite keep = g.getComposite();
                        g.setComposite(java.awt.AlphaComposite.Clear);
                        g.fillRect(left, top, frame.getWidth(), frame.getHeight());
                        g.setComposite(keep);
                    }
                    case "restoreToPrevious" -> {
                        if (previous != null) {
                            java.awt.Composite keep = g.getComposite();
                            g.setComposite(java.awt.AlphaComposite.Src);
                            g.drawImage(previous, 0, 0, null);
                            g.setComposite(keep);
                        }
                    }
                    default -> { }
                }
                frame.flush();
            }
            g.dispose();
            if (frames.isEmpty()) throw new IllegalArgumentException("Empty gif");
            return new Decoded(frames, delays);
        } catch (Throwable t) {
            for (NativeImage frame : built) frame.close();
            throw t;
        } finally {
            reader.dispose();
        }
    }

    /** A cropped frame is composed like a still, an uncropped one only padded. */
    private static NativeImage toSheet(NativeImage frame, CapeCompose.Crop crop, int scale) {
        if (crop == null) return CapeImport.toCapeLayout(frame);
        try {
            return CapeCompose.sheet(frame, crop, scale);
        } finally {
            frame.close();
        }
    }

    /**
     * The first frame as a finished cape sheet, encoded as png. The cape server only
     * takes stills, so this is what everyone else gets to see of an animated cape.
     */
    public static byte[] firstSheetPng(byte[] data, CapeCompose.Crop crop) throws Exception {
        try (NativeImage sheet = firstSheet(data, crop)) {
            int w = sheet.getWidth(), h = sheet.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] abgr = sheet.getPixelsABGR();
            int[] argb = new int[abgr.length];
            for (int i = 0; i < abgr.length; i++) {
                int p = abgr[i];
                argb[i] = ((p >>> 24) << 24) | ((p & 0xFF) << 16)
                        | (((p >> 8) & 0xFF) << 8) | ((p >> 16) & 0xFF);
            }
            out.setRGB(0, 0, w, h, argb, 0, w);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        }
    }

    /** The first frame as a finished cape sheet - what a gallery thumbnail shows. */
    public static NativeImage firstSheet(byte[] data, CapeCompose.Crop crop) throws Exception {
        try (NativeImage frame = firstFrame(data)) {
            if (crop != null) return CapeCompose.sheet(frame, crop, SHEET_SCALE);
            NativeImage copy = new NativeImage(frame.getWidth(), frame.getHeight(), false);
            copy.copyFrom(frame);
            return CapeImport.toCapeLayout(copy);
        }
    }

    /** First frame as it is, for the crop editor's preview. */
    public static NativeImage firstFrame(byte[] data) throws Exception {
        ImageReader reader = reader(data);
        try {
            return toNative(reader.read(0), true);
        } finally {
            reader.dispose();
        }
    }

    /** Uploads the decoded frames. Main thread only; takes the images over. */
    public static GifCape upload(String idBase, Decoded decoded) {
        List<NativeImage> source = decoded.frames();
        Identifier[] ids = new Identifier[source.size()];
        int[] ends = new int[source.size()];
        int width = 64, height = 32, running = 0;
        for (int i = 0; i < source.size(); i++) {
            NativeImage image = source.get(i);
            width = image.getWidth();
            height = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("teslamaps", "cape/" + idBase + "/" + i);
            Minecraft.getInstance().getTextureManager()
                    .register(id, new DynamicTexture(() -> "TeslaMaps animated cape", image));
            ids[i] = id;
            running += decoded.delays().get(i);
            ends[i] = running;
        }
        return new GifCape(ids, ends, width, height);
    }

    private static ImageReader reader(byte[] data) throws Exception {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IllegalArgumentException("No gif reader");
        ImageReader reader = readers.next();
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        reader.setInput(in, false, false);
        return reader;
    }

    private static NativeImage toNative(BufferedImage source, boolean flush) {
        int w = source.getWidth(), h = source.getHeight();
        NativeImage image = new NativeImage(w, h, false);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            source.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                image.setPixelABGR(x, y, (argb & 0xFF00FF00)
                        | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
            }
        }
        if (flush) source.flush();
        return image;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage shrink(BufferedImage source, int width) {
        int height = Math.max(1, source.getHeight() * width / source.getWidth());
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equalsIgnoreCase(name) && node instanceof IIOMetadataNode found) {
                return found;
            }
        }
        return null;
    }

    private static int intAttr(IIOMetadataNode node, String name, int fallback) {
        String value = node.getAttribute(name);
        try {
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
