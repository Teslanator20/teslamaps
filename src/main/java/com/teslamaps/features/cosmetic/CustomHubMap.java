package com.teslamaps.features.cosmetic;

// Ported from FloydAddons' features/impl/render/FloydHubMap.kt + mixin/HubMapRendererMixin.java
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Replaces the 13x7 map wall (hub) with a PNG or GIF from config/teslamaps/hubmap. */
public final class CustomHubMap {

    public static final ImageFolder FOLDER = new ImageFolder("hubmap", ".png", ".gif");
    private static final int COLS = 13, ROWS = 7, TILES = COLS * ROWS;
    private static final int TILE = 128, GIF_TILE = 64, MAX_FRAMES = 32;

    private static final Int2IntOpenHashMap mapped = new Int2IntOpenHashMap();
    private static final Identifier[] ids = new Identifier[TILES];
    private static DynamicTexture[] textures;
    private static int generation;
    private static int tileSize;
    private static int[][] frames;
    private static int[] delays;
    private static int frame;
    private static long lastSwitch;
    private static volatile boolean loading;
    private static boolean loaded;
    private static int timer;

    private CustomHubMap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CustomHubMap").section("Render").when(() -> cfg().customHubMap)
                .tick(CustomHubMap::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Hub Map");
                    b.toggle("Custom Hub Map", () -> c.customHubMap, v -> { c.customHubMap = v; if (!v) mapped.clear(); })
                            .desc("Replaces the big 13x7 map wall in the hub with a PNG or GIF (max 32 frames) from config/teslamaps/hubmap.");
                    b.buttonRow(new String[]{"< Prev", "Next >", "Reload", "Folder"}, new Runnable[]{
                            () -> select(FOLDER.cycle(c.customHubMapFile, -1)),
                            () -> select(FOLDER.cycle(c.customHubMapFile, 1)),
                            () -> { loaded = false; ImageFolder.message("§7Hub map reloaded"); },
                            FOLDER::open});
                });
    }

    private static void select(String name) {
        cfg().customHubMapFile = name;
        TeslaMapsConfig.save();
        loaded = false;
    }

    /** Render thread, from HubMapRendererMixin. */
    public static void apply(MapId id, MapRenderState state) {
        if (mapped.isEmpty() || textures == null || !cfg().customHubMap) return;
        int tile = mapped.getOrDefault(id.id(), -1);
        if (tile < 0) return;
        state.texture = ids[tile];
        state.decorations.clear();
    }

    private static void tick() {
        if (!loaded && !loading) load();
        if (frames != null && frames.length > 1 && textures != null) animate();
        if (++timer % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            mapped.clear();
            return;
        }
        scan();
    }

    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        Map<Long, List<int[]>> planes = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemFrame f)) continue;
            ItemStack stack = f.getItem();
            if (!stack.is(Items.FILLED_MAP)) continue;
            MapId mapId = stack.get(DataComponents.MAP_ID);
            Direction d = f.getDirection();
            if (mapId == null || !d.getAxis().isHorizontal()) continue;
            BlockPos p = f.blockPosition();
            int depth = d.getAxis() == Direction.Axis.Z ? p.getZ() : p.getX();
            int h = switch (d) {
                case SOUTH -> p.getX();
                case NORTH -> -p.getX();
                case WEST -> p.getZ();
                default -> -p.getZ();
            };
            planes.computeIfAbsent(((long) d.ordinal() << 32) | (depth & 0xFFFFFFFFL), k -> new ArrayList<>())
                    .add(new int[]{mapId.id(), h, p.getY()});
        }
        for (List<int[]> cells : planes.values()) {
            if (cells.size() < TILES) continue;
            TreeSet<Integer> hs = new TreeSet<>(), vs = new TreeSet<>();
            for (int[] c : cells) { hs.add(c[1]); vs.add(c[2]); }
            if (hs.size() != COLS || vs.size() != ROWS) continue;
            List<Integer> hl = new ArrayList<>(hs), vl = new ArrayList<>(vs.descendingSet());
            Int2IntOpenHashMap next = new Int2IntOpenHashMap();
            boolean[] used = new boolean[TILES];
            boolean ok = true;
            for (int[] c : cells) {
                int tile = vl.indexOf(c[2]) * COLS + hl.indexOf(c[1]);
                if (used[tile]) { ok = false; break; }
                used[tile] = true;
                next.put(c[0], tile);
            }
            if (ok && next.size() == TILES) {
                mapped.clear();
                mapped.putAll(next);
                return;
            }
        }
    }

    private static void load() {
        loaded = true;
        Path file = FOLDER.resolve(cfg().customHubMapFile);
        if (file == null) {
            release();
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            int[][] decoded = null;
            int[] decodedDelays = null;
            int size = TILE;
            try {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")) {
                    List<int[]> fs = new ArrayList<>();
                    List<Integer> ds = new ArrayList<>();
                    size = readGif(file, fs, ds);
                    decoded = fs.toArray(new int[0][]);
                    decodedDelays = ds.stream().mapToInt(Integer::intValue).toArray();
                } else {
                    BufferedImage img;
                    try (var in = Files.newInputStream(file)) { img = ImageIO.read(in); }
                    if (img != null) {
                        decoded = new int[][]{scaled(img, TILE)};
                        decodedDelays = new int[]{100};
                    }
                }
            } catch (Throwable e) {
                TeslaMaps.LOGGER.warn("Hub map {} failed to load: {}", file.getFileName(), e.toString());
            }
            int[][] fFrames = decoded;
            int[] fDelays = decodedDelays;
            int fSize = size;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                if (fFrames == null || fFrames.length == 0) {
                    release();
                    return;
                }
                upload(fFrames, fDelays, fSize);
            });
        }, "TeslaMaps hub map");
        t.setDaemon(true);
        t.start();
    }

    private static void release() {
        if (textures != null) {
            for (Identifier id : ids) if (id != null) Minecraft.getInstance().getTextureManager().release(id);
        }
        textures = null;
        frames = null;
    }

    private static void upload(int[][] newFrames, int[] newDelays, int size) {
        release();
        generation++;
        frames = newFrames;
        delays = newDelays;
        tileSize = size;
        frame = 0;
        lastSwitch = System.currentTimeMillis();
        textures = new DynamicTexture[TILES];
        for (int i = 0; i < TILES; i++) {
            ids[i] = Identifier.fromNamespaceAndPath("teslamaps", "hubmap/" + generation + "_" + i);
            NativeImage img = new NativeImage(size, size, false);
            fill(img, frames[0], i, size);
            textures[i] = new DynamicTexture(() -> "TeslaMaps hub map", img);
            Minecraft.getInstance().getTextureManager().register(ids[i], textures[i]);
        }
    }

    private static void animate() {
        long now = System.currentTimeMillis();
        if (now - lastSwitch < delays[frame]) return;
        lastSwitch = now;
        frame = (frame + 1) % frames.length;
        for (int i = 0; i < TILES; i++) {
            NativeImage img = textures[i].getPixels();
            if (img == null) continue;
            fill(img, frames[frame], i, tileSize);
            textures[i].upload();
        }
    }

    private static void fill(NativeImage img, int[] px, int tile, int size) {
        int width = COLS * size;
        int ox = (tile % COLS) * size, oy = (tile / COLS) * size;
        for (int y = 0; y < size; y++) {
            int row = (oy + y) * width + ox;
            for (int x = 0; x < size; x++) img.setPixel(x, y, px[row + x]);
        }
    }

    private static int[] scaled(BufferedImage src, int size) {
        int w = COLS * size, h = ROWS * size;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out.getRGB(0, 0, w, h, null, 0, w);
    }

    private static int readGif(Path file, List<int[]> out, List<Integer> delaysOut) throws Exception {
        try (var in = Files.newInputStream(file); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            var readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IllegalStateException("no gif reader");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int count = reader.getNumImages(true);
                int size = count > 1 ? GIF_TILE : TILE;
                int n = Math.min(count, MAX_FRAMES);
                BufferedImage canvas = new BufferedImage(reader.getWidth(0), reader.getHeight(0), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                for (int i = 0; i < n; i++) {
                    IIOMetadata meta = reader.getImageMetadata(i);
                    BufferedImage raw = reader.read(i);
                    Node gce = node(meta, "GraphicControlExtension"), desc = node(meta, "ImageDescriptor");
                    int fx = intAttr(desc, "imageLeftPosition"), fy = intAttr(desc, "imageTopPosition");
                    String disposal = attr(gce, "disposalMethod");
                    BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                    g.drawImage(raw, fx, fy, null);
                    out.add(scaled(canvas, size));
                    int delay = intAttr(gce, "delayTime") * 10;
                    delaysOut.add(delay > 0 ? Math.max(delay, 20) : 100);
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        g.setComposite(java.awt.AlphaComposite.Clear);
                        g.fillRect(fx, fy, raw.getWidth(), raw.getHeight());
                        g.setComposite(java.awt.AlphaComposite.SrcOver);
                    } else if (before != null) {
                        g.setComposite(java.awt.AlphaComposite.Src);
                        g.drawImage(before, 0, 0, null);
                        g.setComposite(java.awt.AlphaComposite.SrcOver);
                    }
                }
                g.dispose();
                return size;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private static Node node(IIOMetadata meta, String name) {
        String format = meta.getNativeMetadataFormatName();
        return format == null ? null : find(meta.getAsTree(format), name);
    }

    private static Node find(Node n, String name) {
        if (n.getNodeName().equals(name)) return n;
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            Node f = find(c, name);
            if (f != null) return f;
        }
        return null;
    }

    private static String attr(Node n, String name) {
        if (n == null || n.getAttributes() == null) return null;
        Node a = n.getAttributes().getNamedItem(name);
        return a == null ? null : a.getNodeValue();
    }

    private static int intAttr(Node n, String name) {
        try {
            String v = attr(n, name);
            return v == null ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
